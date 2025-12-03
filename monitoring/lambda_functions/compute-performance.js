/**
 * Compute Performance Monitor - Enterprise Grade
 * * 이 Lambda 함수는 컴퓨팅 리소스(ECS, Fargate)의 성능을 심층 분석합니다.
 * 단순 상태 확인을 넘어, CloudWatch 메트릭을 통해 리소스 사용률 추이와
 * 애플리케이션 응답 속도(Latency)를 모니터링하여 병목 현상을 감지합니다.
 * * 주요 기능:
 * 1. 실시간 메트릭 조회: CPU, Memory 사용률 및 네트워크 I/O 분석
 * 2. 시계열 데이터 처리: 차트 렌더링을 위한 1시간치 데이터 집계
 * 3. 응답 속도 분석: 평균 응답 시간 및 P99 지연 시간 측정
 * 4. 결함 감내(Fault Tolerance): 권한 부족 시 시뮬레이션 모드로 자동 전환
 */

const { ECSClient, DescribeServicesCommand } = require("@aws-sdk/client-ecs");
const { CloudWatchClient, GetMetricStatisticsCommand } = require("@aws-sdk/client-cloudwatch");
const { ElasticLoadBalancingV2Client, DescribeLoadBalancersCommand } = require("@aws-sdk/client-elastic-load-balancing-v2");

// AWS 클라이언트 초기화 (ap-northeast-2 리전 고정)
const ecs = new ECSClient({ region: 'ap-northeast-2' });
const cw = new CloudWatchClient({ region: 'ap-northeast-2' });
const elbv2 = new ElasticLoadBalancingV2Client({ region: 'ap-northeast-2' });

// 모니터링 대상 리소스 설정
const CONFIG = {
    CLUSTER_NAME: 'gitnote-cluster',
    SERVICE_NAME: 'gitnote-service',
    ALB_NAME_TAG: 'gitnote-alb'
};

exports.handler = async (event) => {
    try {
        console.log("컴퓨팅 리소스 성능 분석을 시작합니다...");

        // 1. 실제 데이터 조회 시도 (모든 데이터를 병렬로 조회하여 지연 시간 최소화)
        // Promise.all을 사용하여 서로 의존성이 없는 데이터를 동시에 가져옵니다.
        const [serviceData, metricsData, responseTimeData] = await Promise.all([
            getECSServiceHealth(),
            getHistoricalMetrics(),
            getALBLatency()
        ]);

        // 2. 서비스 데이터에 응답 시간 메트릭 병합
        // 프론트엔드에서 개별 서비스 카드에 응답 시간을 표시하기 위함입니다.
        if (serviceData.length > 0) {
            serviceData[0].responseTime = responseTimeData.avg;
        }

        // 3. 수집된 데이터를 기반으로 이상 징후 감지
        const alerts = analyzePerformance(serviceData, metricsData, responseTimeData);

        return createResponse({
            services: serviceData,
            metrics: metricsData,
            avgResponseTime: responseTimeData.avg,
            p99ResponseTime: responseTimeData.p99,
            alerts: alerts
        });

    } catch (error) {
        console.warn("실제 데이터 조회 실패 (시뮬레이션 모드로 전환합니다):", error.message);
        
        // 4. 권한 부족이나 리소스 부재 시, 대시보드 중단을 막기 위해 시뮬레이션 데이터 반환
        const simData = generateSimulationData();
        
        return createResponse({
            ...simData,
            alerts: [{
                severity: 'warning',
                title: '시뮬레이션 데이터 표시 중',
                message: `실제 메트릭 조회 실패: ${error.message}. 권한(CloudWatch/ECS)을 확인하세요.`,
                timestamp: new Date().toISOString()
            }]
        });
    }
};

/**
 * 표준화된 API 응답 객체를 생성합니다.
 * CORS 헤더와 타임스탬프를 자동으로 포함합니다.
 */
function createResponse(body) {
    return {
        statusCode: 200,
        headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Headers': 'Content-Type',
            'Access-Control-Allow-Methods': 'GET, OPTIONS'
        },
        body: JSON.stringify({
            ...body,
            timestamp: new Date().toISOString()
        })
    };
}

/**
 * ECS 서비스의 실행 상태(Running vs Desired)를 조회합니다.
 */
async function getECSServiceHealth() {
    const command = new DescribeServicesCommand({
        cluster: CONFIG.CLUSTER_NAME,
        services: [CONFIG.SERVICE_NAME]
    });

    const response = await ecs.send(command);
    const service = response.services?.[0];

    if (!service) {
        throw new Error(`ECS 서비스 '${CONFIG.SERVICE_NAME}'를 찾을 수 없습니다.`);
    }

    // 현재 CPU/Memory 사용률 스냅샷 조회 (최근 5분 평균)
    const usage = await getCurrentUtilization();

    return [{
        name: service.serviceName,
        // 실행 중인 태스크가 목표 개수와 일치하면 건강함(Healthy)
        health: (service.runningCount === service.desiredCount && service.runningCount > 0) ? 'healthy' : 'degraded',
        status: service.status,
        runningTasks: service.runningCount,
        desiredTasks: service.desiredCount,
        cpu: usage.cpu,
        memory: usage.memory,
        responseTime: 0, // 나중에 병합됨
        deployments: service.deployments || []
    }];
}

/**
 * CloudWatch에서 최근 1시간 동안의 CPU/Memory 사용률 추이를 조회합니다.
 * 이 데이터는 프론트엔드에서 라인 차트를 그리는 데 사용됩니다.
 */
async function getHistoricalMetrics() {
    const endTime = new Date();
    const startTime = new Date(endTime.getTime() - 3600000); // 1시간 전

    // CPU와 메모리 메트릭을 동시에 조회
    const [cpuRes, memRes] = await Promise.all([
        fetchCloudWatchMetric('CPUUtilization', startTime, endTime),
        fetchCloudWatchMetric('MemoryUtilization', startTime, endTime)
    ]);

    // CloudWatch 데이터는 시간순 정렬이 보장되지 않으므로 타임스탬프 기준 정렬 수행
    const sortPoints = (datapoints) => (datapoints || []).sort((a, b) => a.Timestamp - b.Timestamp);
    
    const cpuPoints = sortPoints(cpuRes.Datapoints);
    const memPoints = sortPoints(memRes.Datapoints);

    const timestamps = [];
    const cpuValues = [];
    const memValues = [];

    // 데이터 포인트 매핑
    cpuPoints.forEach(point => {
        timestamps.push(point.Timestamp.toISOString());
        cpuValues.push(parseFloat(point.Average.toFixed(2)));

        // 동일 시간대의 메모리 데이터 찾기 (없으면 0 처리)
        const memPoint = memPoints.find(m => m.Timestamp.getTime() === point.Timestamp.getTime());
        memValues.push(memPoint ? parseFloat(memPoint.Average.toFixed(2)) : 0);
    });

    return {
        timestamps: timestamps,
        cpu: cpuValues,
        memory: memValues
    };
}

/**
 * CloudWatch 메트릭 조회를 위한 헬퍼 함수
 */
async function fetchCloudWatchMetric(metricName, start, end) {
    return cw.send(new GetMetricStatisticsCommand({
        Namespace: 'AWS/ECS',
        MetricName: metricName,
        Dimensions: [
            { Name: 'ClusterName', Value: CONFIG.CLUSTER_NAME },
            { Name: 'ServiceName', Value: CONFIG.SERVICE_NAME }
        ],
        StartTime: start,
        EndTime: end,
        Period: 300, // 5분 단위 집계
        Statistics: ['Average']
    }));
}

/**
 * 현재 시점의 리소스 사용률을 조회합니다.
 * 그래프용 데이터와 달리 가장 최근의 단일 값을 반환합니다.
 */
async function getCurrentUtilization() {
    const end = new Date();
    const start = new Date(end.getTime() - 600000); // 최근 10분
    
    const [cpuRes, memRes] = await Promise.all([
        fetchCloudWatchMetric('CPUUtilization', start, end),
        fetchCloudWatchMetric('MemoryUtilization', start, end)
    ]);

    // 데이터가 없으면 0 반환
    const getLatest = (res) => {
        if (!res.Datapoints || res.Datapoints.length === 0) return 0;
        // 최신순 정렬 후 첫 번째 값
        return res.Datapoints.sort((a, b) => b.Timestamp - a.Timestamp)[0].Average;
    };

    return {
        cpu: parseFloat(getLatest(cpuRes).toFixed(2)),
        memory: parseFloat(getLatest(memRes).toFixed(2))
    };
}

/**
 * ALB(로드밸런서)의 응답 속도(Latency)를 조회합니다.
 * Average(평균)와 P99(상위 1% 느린 요청)를 모두 측정하여 성능 병목을 파악합니다.
 */
async function getALBLatency() {
    try {
        // 로드밸런서 ARN 조회
        const lbRes = await elbv2.send(new DescribeLoadBalancersCommand({ Names: [CONFIG.ALB_NAME_TAG] }));
        if (!lbRes.LoadBalancers?.[0]) return { avg: 0, p99: 0 };

        const albArn = lbRes.LoadBalancers[0].LoadBalancerArn;
        // CloudWatch Dimensions 형식에 맞게 ARN 파싱 (app/name/id)
        const albDimension = albArn.split('/').slice(-3).join('/');

        const command = new GetMetricStatisticsCommand({
            Namespace: 'AWS/ApplicationELB',
            MetricName: 'TargetResponseTime',
            Dimensions: [{ Name: 'LoadBalancer', Value: albDimension }],
            StartTime: new Date(Date.now() - 3600000), // 최근 1시간
            EndTime: new Date(),
            Period: 3600,
            Statistics: ['Average'],
            ExtendedStatistics: ['p99'] // 꼬리 지연(Tail Latency) 확인용
        });

        const res = await cw.send(command);
        
        if (res.Datapoints && res.Datapoints.length > 0) {
            const data = res.Datapoints[0];
            return {
                avg: parseFloat((data.Average * 1000).toFixed(2)), // 초 -> 밀리초 변환
                p99: parseFloat((data.ExtendedStatistics['p99'] * 1000).toFixed(2))
            };
        }
    } catch (error) {
        // ALB가 없거나 메트릭이 없는 경우, 전체 로직을 중단하지 않고 0을 반환
        console.warn("ALB 메트릭 조회 실패 (무시됨):", error.message);
    }
    return { avg: 0, p99: 0 };
}

/**
 * 성능 데이터를 분석하여 경고(Alert)를 생성합니다.
 */
function analyzePerformance(services, metrics, responseTime) {
    const alerts = [];

    // 1. 태스크 실행 상태 확인
    services.forEach(svc => {
        if (svc.runningTasks < svc.desiredTasks) {
            alerts.push({
                severity: 'danger',
                title: '서비스 안정성 경고',
                message: `실행 중인 태스크(${svc.runningTasks})가 목표 개수(${svc.desiredTasks})보다 적습니다.`
            });
        }
    });

    // 2. 응답 속도 지연 확인 (SLA 기준: 1000ms)
    if (responseTime.avg > 1000) {
        alerts.push({
            severity: 'warning',
            title: '응답 속도 지연',
            message: `평균 응답 시간이 ${responseTime.avg}ms로 임계치를 초과했습니다.`
        });
    }

    return alerts;
}

/**
 * 실제 데이터 조회가 불가능할 때 사용하는 시뮬레이션 데이터 생성기입니다.
 * 단순 랜덤이 아니라, 실제 서버 패턴처럼 보이도록 사인파(Sine Wave) 알고리즘을 사용합니다.
 */
function generateSimulationData() {
    const timestamps = [];
    const cpu = [];
    const memory = [];
    const now = new Date();
    
    // 최근 1시간 데이터 생성
    for (let i = 12; i >= 0; i--) {
        const time = new Date(now - i * 300000);
        timestamps.push(time.toISOString());
        
        // 시간 흐름에 따른 자연스러운 부하 변화 시뮬레이션
        const timeOffset = time.getTime() / 100000;
        const baseLoad = 20 + Math.sin(timeOffset) * 10; // 10~30% 사이의 파동
        
        cpu.push(parseFloat((baseLoad + Math.random() * 5).toFixed(2)));
        memory.push(parseFloat((baseLoad + 25 + Math.random() * 3).toFixed(2)));
    }

    const currentCpu = cpu[cpu.length - 1];
    const currentMem = memory[memory.length - 1];

    return {
        services: [{
            name: 'gitnote-service',
            health: 'healthy',
            cpu: currentCpu,
            memory: currentMem,
            runningTasks: 2,
            desiredTasks: 2,
            responseTime: 45.5,
            deployments: [{ status: 'PRIMARY', runningCount: 2, desiredCount: 2 }]
        }],
        metrics: {
            timestamps: timestamps,
            cpu: cpu,
            memory: memory
        },
        avgResponseTime: 45.5,
        p99ResponseTime: 120.2
    };
}