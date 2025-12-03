/**
 * 문제점: AWS CloudWatch의 S3 메트릭은 하루 1회 집계되어 최대 24시간의 데이터 지연이 발생함.
 * 해결책: ListObjectsV2 API를 사용하여 버킷 내 객체를 직접 전수 조사하는 '실시간 스캔 전략'을 도입함.
 *
 * [핵심 기능]
 * 1. 실시간 객체 스캔: 메타데이터를 직접 순회하여 현재 시점의 정확한 용량과 개수를 산출
 * 2. 병렬 처리 아키텍처: S3와 DynamoDB 진단을 동시에 수행하여 Lambda 실행 시간 최소화
 * 3. 결함 격리(Fault Isolation): 개별 리소스 조회 실패가 전체 모니터링 시스템의 중단으로 이어지지 않도록 예외 처리
 * 4. 가독성 데이터 변환: 바이트 단위의 데이터를 사용자가 읽기 쉬운 단위(KB, MB, GB)로 자동 변환
 */

const { S3Client, ListObjectsV2Command, HeadBucketCommand } = require("@aws-sdk/client-s3");
const { DynamoDBClient, DescribeTableCommand } = require("@aws-sdk/client-dynamodb");

// TCP 연결 재사용을 위해 핸들러 외부에서 클라이언트 초기화
const s3 = new S3Client({ region: 'ap-northeast-2' });
const ddb = new DynamoDBClient({ region: 'ap-northeast-2' });

// 모니터링 대상 리소스 설정
const CONFIG = {
    BUCKET_NAME: 'gitnote-s3-bucket',
    TABLE_NAMES: ['UserPreset', 'UserReports']
};

exports.handler = async (event) => {
    try {
        console.log("[System] 실시간 스토리지 분석 프로세스를 시작합니다...");

        // 스캐터-개더(Scatter-Gather) 패턴을 적용하여 병렬로 데이터 수집
        const [s3Data, ddbData] = await Promise.all([
            scanS3Realtime(),
            analyzeDynamoDB()
        ]);

        // 데이터가 전혀 수집되지 않은 경우, UI 렌더링 오류 방지를 위해 플레이스홀더 데이터 주입
        if (s3Data.length === 0 && ddbData.length === 0) {
            console.warn("[System] 리소스가 감지되지 않았습니다. 초기화 데이터를 생성합니다.");
            s3Data.push(createPlaceholder());
        }

        // 수집된 데이터 통합
        const allStorage = [...s3Data, ...ddbData];
        
        // 대시보드 상단 요약을 위한 전체 메트릭 집계
        const metrics = calculateAggregates(allStorage);

        // 리소스 상태에 따른 알림 생성
        const alerts = generateAlerts(allStorage);

        return createResponse({
            storage: allStorage,
            metrics: metrics,
            alerts: alerts
        });

    } catch (error) {
        console.error("[System Critical] 스토리지 분석 중 치명적 오류 발생:", error);
        return createErrorResponse(error);
    }
};

/**
 * S3 버킷의 실시간 상태를 스캔하여 정확한 용량과 객체 수를 계산합니다.
 * CloudWatch의 지연 시간을 극복하기 위해 객체 메타데이터를 직접 순회합니다.
 */
async function scanS3Realtime() {
    const bucket = CONFIG.BUCKET_NAME;
    let totalSize = 0;
    let totalCount = 0;
    let isTruncated = true;
    let continuationToken;

    try {
        // 1단계: 버킷 존재 여부 및 접근 권한 확인
        await s3.send(new HeadBucketCommand({ Bucket: bucket }));

        // 2단계: 객체 목록 전체 순회 (1000개 이상의 객체에 대한 페이지네이션 처리)
        while (isTruncated) {
            const cmd = new ListObjectsV2Command({
                Bucket: bucket,
                ContinuationToken: continuationToken
            });
            const res = await s3.send(cmd);

            if (res.Contents) {
                for (const item of res.Contents) {
                    totalSize += item.Size || 0;
                    totalCount++;
                }
            }

            // 다음 페이지 존재 여부 확인
            isTruncated = res.IsTruncated;
            continuationToken = res.NextContinuationToken;
        }

        return [{
            name: bucket,
            type: 'S3',
            status: 'healthy',
            size: totalSize,
            itemCount: totalCount,
            details: { 'Region': 'ap-northeast-2', 'Scan Mode': 'Real-time Direct' },
            metrics: { 
                'Storage Size': formatBytes(totalSize), 
                'Object Count': totalCount.toLocaleString() 
            }
        }];

    } catch (error) {
        console.error(`[S3] 버킷(${bucket}) 스캔 실패:`, error.message);
        
        // 에러 발생 시에도 전체 프로세스를 중단하지 않고 실패 상태를 반환
        return [{
            name: bucket,
            type: 'S3',
            status: 'critical',
            size: 0,
            itemCount: 0,
            details: { 'Error': 'Bucket Not Found or Access Denied' },
            metrics: { 'Storage Size': '-', 'Object Count': '-' }
        }];
    }
}

/**
 * DynamoDB 테이블의 상태와 프로비저닝된 용량을 확인합니다.
 */
async function analyzeDynamoDB() {
    const results = [];
    
    // 설정된 모든 테이블에 대해 순차적으로 상태 점검 수행
    for (const tName of CONFIG.TABLE_NAMES) {
        try {
            const res = await ddb.send(new DescribeTableCommand({ TableName: tName }));
            if (res.Table) {
                const size = res.Table.TableSizeBytes || 0;
                const count = res.Table.ItemCount || 0;
                
                results.push({
                    name: tName,
                    type: 'DynamoDB',
                    status: res.Table.TableStatus === 'ACTIVE' ? 'healthy' : 'warning',
                    size: size,
                    itemCount: count,
                    details: { 'Status': res.Table.TableStatus },
                    metrics: { 'Table Size': formatBytes(size), 'Item Count': count.toLocaleString() }
                });
            }
        } catch (e) {
            // 테이블을 찾을 수 없는 경우 경고 상태로 기록
            results.push({
                name: tName,
                type: 'DynamoDB',
                status: 'critical',
                size: 0,
                itemCount: 0,
                details: { 'Error': 'Table Not Found' },
                metrics: { 'Table Size': '-', 'Item Count': '-' }
            });
        }
    }
    return results;
}

// --- 헬퍼 함수 영역 ---

/**
 * 대시보드 요약을 위해 수집된 개별 리소스의 메트릭을 합산합니다.
 */
function calculateAggregates(resources) {
    return {
        totalStorageSize: resources.reduce((acc, curr) => acc + (curr.size || 0), 0),
        totalObjectCount: resources.reduce((acc, curr) => acc + (curr.itemCount || 0), 0),
        
        // 향후 확장을 위해 0으로 초기화된 메트릭 필드 유지
        totalDynamoDBItems: 0, 
        totalDynamoDBSize: 0, 
        s3RequestCount: 0, 
        dynamoDBRequestCount: 0
    };
}

/**
 * 비정상 상태인 리소스를 감지하여 알림 객체를 생성합니다.
 */
function generateAlerts(resources) {
    const alerts = [];
    resources.forEach(r => {
        if (r.status !== 'healthy') {
            alerts.push({ 
                severity: 'danger', 
                title: `${r.type} 가용성 문제 감지`, 
                message: `리소스 '${r.name}'의 상태를 확인하십시오. 상세: ${r.details.Error || r.details.Status}`,
                timestamp: new Date().toISOString()
            });
        }
    });
    return alerts;
}

/**
 * 리소스 부재 시 UI 깨짐 방지를 위한 더미 데이터 생성
 */
function createPlaceholder() {
    return {
        name: 'System-Initialization',
        type: 'S3',
        status: 'healthy',
        size: 1024,
        itemCount: 1,
        details: { 'Info': 'Waiting for Resources' },
        metrics: { 'Storage Size': '1 KB', 'Object Count': '1' }
    };
}

/**
 * 표준화된 API 응답 포맷 생성
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
 * 시스템 오류 발생 시 안전한 에러 응답 생성
 */
function createErrorResponse(error) {
    return createResponse({
        storage: [],
        metrics: { totalStorageSize:0, totalObjectCount:0, totalDynamoDBItems:0, totalDynamoDBSize:0, s3RequestCount:0, dynamoDBRequestCount:0 },
        alerts: [{
            severity: 'danger',
            title: '시스템 치명적 오류',
            message: error.message,
            timestamp: new Date().toISOString()
        }]
    });
}

/**
 * 바이트 단위의 숫자를 사람이 읽기 쉬운 포맷(KB, MB, GB)으로 변환합니다.
 */
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}