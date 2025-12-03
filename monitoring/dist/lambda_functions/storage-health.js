"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.handler = void 0;
const AWS = __importStar(require("aws-sdk"));
// AWS SDK 클라이언트 초기화
const s3 = new AWS.S3({ region: 'ap-northeast-2' });
const dynamodb = new AWS.DynamoDB({ region: 'ap-northeast-2' });
const cloudwatch = new AWS.CloudWatch({ region: 'ap-northeast-2' });
/**
 * GitNote Storage Health Lambda Handler
 * S3 버킷과 DynamoDB 테이블의 상태 및 성능을 모니터링
 */
const handler = async (event) => {
    console.log('Storage Health Request:', JSON.stringify(event));
    try {
        // S3 버킷 상태 조회
        const s3Status = await getS3BucketStatus();
        // DynamoDB 테이블 상태 조회
        const dynamoStatus = await getDynamoDBStatus();
        // 스토리지 메트릭 조회
        const storageMetrics = await getStorageMetrics();
        // 이상 징후 감지
        const alerts = detectStorageIssues(s3Status, dynamoStatus, storageMetrics);
        // 응답 구성
        const response = {
            storage: [
                ...s3Status,
                ...dynamoStatus
            ],
            metrics: storageMetrics,
            alerts,
            timestamp: new Date().toISOString()
        };
        return {
            statusCode: 200,
            headers: {
                'Content-Type': 'application/json',
                'Access-Control-Allow-Origin': '*',
                'Access-Control-Allow-Headers': 'Content-Type',
                'Access-Control-Allow-Methods': 'GET, OPTIONS'
            },
            body: JSON.stringify(response)
        };
    }
    catch (error) {
        console.error('Error in storage-health handler:', error);
        return {
            statusCode: 500,
            headers: {
                'Content-Type': 'application/json',
                'Access-Control-Allow-Origin': '*'
            },
            body: JSON.stringify({
                error: 'Failed to retrieve storage health',
                message: error instanceof Error ? error.message : 'Unknown error',
                timestamp: new Date().toISOString()
            })
        };
    }
};
exports.handler = handler;
/**
 * S3 버킷 상태 및 메트릭 조회
 */
async function getS3BucketStatus() {
    const buckets = [];
    const bucketName = 'gitnote-s3-bucket';
    try {
        // 버킷 존재 확인
        await s3.headBucket({ Bucket: bucketName }).promise();
        // 버킷 버저닝 상태
        let versioningStatus = 'Disabled';
        try {
            const versioning = await s3.getBucketVersioning({ Bucket: bucketName }).promise();
            versioningStatus = versioning.Status || 'Disabled';
        }
        catch (error) {
            console.error('Error getting versioning status:', error);
        }
        // 버킷 암호화 상태
        let encryptionStatus = 'Disabled';
        try {
            await s3.getBucketEncryption({ Bucket: bucketName }).promise();
            encryptionStatus = 'Enabled';
        }
        catch (error) {
            if (error.code === 'ServerSideEncryptionConfigurationNotFoundError') {
                encryptionStatus = 'Disabled';
            }
        }
        // 버킷 크기 및 객체 수 (CloudWatch 메트릭)
        const bucketSize = await getS3BucketSize(bucketName);
        const objectCount = await getS3ObjectCount(bucketName);
        // 버킷 라이프사이클 정책
        let lifecycleStatus = 'Not Configured';
        try {
            const lifecycle = await s3.getBucketLifecycleConfiguration({ Bucket: bucketName }).promise();
            if (lifecycle.Rules && lifecycle.Rules.length > 0) {
                lifecycleStatus = `${lifecycle.Rules.length} rules configured`;
            }
        }
        catch (error) {
            if (error.code !== 'NoSuchLifecycleConfiguration') {
                console.error('Error getting lifecycle:', error);
            }
        }
        // 버킷 액세스 로깅
        let loggingStatus = 'Disabled';
        try {
            const logging = await s3.getBucketLogging({ Bucket: bucketName }).promise();
            if (logging.LoggingEnabled) {
                loggingStatus = 'Enabled';
            }
        }
        catch (error) {
            console.error('Error getting logging status:', error);
        }
        buckets.push({
            name: bucketName,
            type: 'S3',
            status: 'healthy',
            size: bucketSize,
            itemCount: objectCount,
            details: {
                'Versioning': versioningStatus,
                'Encryption': encryptionStatus,
                'Lifecycle': lifecycleStatus,
                'Access Logging': loggingStatus,
                'Region': 'ap-northeast-2'
            },
            metrics: {
                'Storage Size': formatBytes(bucketSize),
                'Object Count': objectCount.toLocaleString(),
                'Avg Object Size': objectCount > 0 ? formatBytes(bucketSize / objectCount) : '0 B'
            }
        });
    }
    catch (error) {
        console.error('Error getting S3 bucket status:', error);
        if (error.code === 'NotFound' || error.code === 'NoSuchBucket') {
            buckets.push({
                name: bucketName,
                type: 'S3',
                status: 'critical',
                size: 0,
                itemCount: 0,
                error: 'Bucket not found'
            });
        }
        else {
            buckets.push({
                name: bucketName,
                type: 'S3',
                status: 'critical',
                size: 0,
                itemCount: 0,
                error: 'Failed to retrieve bucket information'
            });
        }
    }
    return buckets;
}
/**
 * DynamoDB 테이블 상태 및 메트릭 조회
 */
async function getDynamoDBStatus() {
    const tables = [];
    const tableNames = ['UserPreset', 'UserReports'];
    for (const tableName of tableNames) {
        try {
            // 테이블 상태 조회
            const tableInfo = await dynamodb.describeTable({ TableName: tableName }).promise();
            if (tableInfo.Table) {
                const table = tableInfo.Table;
                // CloudWatch 메트릭 조회 (throttles, consumed capacity)
                const throttleMetrics = await getDynamoDBThrottles(tableName);
                const consumedCapacity = await getDynamoDBConsumedCapacity(tableName);
                // 테이블 상태 판단
                const status = table.TableStatus === 'ACTIVE' && throttleMetrics.readThrottles === 0 && throttleMetrics.writeThrottles === 0
                    ? 'healthy'
                    : throttleMetrics.readThrottles > 0 || throttleMetrics.writeThrottles > 0
                        ? 'warning'
                        : 'degraded';
                tables.push({
                    name: tableName,
                    type: 'DynamoDB',
                    status,
                    size: table.TableSizeBytes || 0,
                    itemCount: table.ItemCount || 0,
                    throttles: throttleMetrics.readThrottles + throttleMetrics.writeThrottles,
                    details: {
                        'Table Status': table.TableStatus,
                        'Billing Mode': table.BillingModeSummary?.BillingMode || 'PROVISIONED',
                        'Read Capacity': table.ProvisionedThroughput?.ReadCapacityUnits || 'On-Demand',
                        'Write Capacity': table.ProvisionedThroughput?.WriteCapacityUnits || 'On-Demand',
                        'Indexes': table.GlobalSecondaryIndexes?.length || 0,
                        'Streams': table.StreamSpecification?.StreamEnabled ? 'Enabled' : 'Disabled'
                    },
                    metrics: {
                        'Item Count': (table.ItemCount || 0).toLocaleString(),
                        'Table Size': formatBytes(table.TableSizeBytes || 0),
                        'Read Throttles': throttleMetrics.readThrottles,
                        'Write Throttles': throttleMetrics.writeThrottles,
                        'Consumed RCU': consumedCapacity.readCapacity.toFixed(2),
                        'Consumed WCU': consumedCapacity.writeCapacity.toFixed(2)
                    }
                });
                // Global Secondary Indexes 상태 확인
                if (table.GlobalSecondaryIndexes) {
                    for (const gsi of table.GlobalSecondaryIndexes) {
                        if (gsi.IndexStatus !== 'ACTIVE') {
                            tables.push({
                                name: `${tableName}/${gsi.IndexName}`,
                                type: 'DynamoDB GSI',
                                status: 'warning',
                                details: {
                                    'Index Status': gsi.IndexStatus,
                                    'Item Count': gsi.ItemCount || 0,
                                    'Index Size': formatBytes(gsi.IndexSizeBytes || 0)
                                }
                            });
                        }
                    }
                }
            }
        }
        catch (error) {
            console.error(`Error getting DynamoDB table ${tableName} status:`, error);
            if (error.code === 'ResourceNotFoundException') {
                tables.push({
                    name: tableName,
                    type: 'DynamoDB',
                    status: 'critical',
                    size: 0,
                    itemCount: 0,
                    error: 'Table not found'
                });
            }
            else {
                tables.push({
                    name: tableName,
                    type: 'DynamoDB',
                    status: 'critical',
                    size: 0,
                    itemCount: 0,
                    error: 'Failed to retrieve table information'
                });
            }
        }
    }
    return tables;
}
/**
 * S3 버킷 크기 조회 (CloudWatch 메트릭)
 */
async function getS3BucketSize(bucketName) {
    const endTime = new Date();
    const startTime = new Date(endTime.getTime() - 86400000); // 24시간 전
    try {
        const response = await cloudwatch.getMetricStatistics({
            Namespace: 'AWS/S3',
            MetricName: 'BucketSizeBytes',
            Dimensions: [
                { Name: 'BucketName', Value: bucketName },
                { Name: 'StorageType', Value: 'StandardStorage' }
            ],
            StartTime: startTime,
            EndTime: endTime,
            Period: 86400,
            Statistics: ['Average']
        }).promise();
        if (response.Datapoints && response.Datapoints.length > 0) {
            return response.Datapoints[0].Average || 0;
        }
    }
    catch (error) {
        console.error('Error getting S3 bucket size:', error);
    }
    return 0;
}
/**
 * S3 객체 수 조회 (CloudWatch 메트릭)
 */
async function getS3ObjectCount(bucketName) {
    const endTime = new Date();
    const startTime = new Date(endTime.getTime() - 86400000);
    try {
        const response = await cloudwatch.getMetricStatistics({
            Namespace: 'AWS/S3',
            MetricName: 'NumberOfObjects',
            Dimensions: [
                { Name: 'BucketName', Value: bucketName },
                { Name: 'StorageType', Value: 'AllStorageTypes' }
            ],
            StartTime: startTime,
            EndTime: endTime,
            Period: 86400,
            Statistics: ['Average']
        }).promise();
        if (response.Datapoints && response.Datapoints.length > 0) {
            return Math.round(response.Datapoints[0].Average || 0);
        }
    }
    catch (error) {
        console.error('Error getting S3 object count:', error);
    }
    // 메트릭이 없는 경우 직접 카운트 (제한적)
    try {
        const objects = await s3.listObjectsV2({
            Bucket: bucketName,
            MaxKeys: 1000
        }).promise();
        return objects.KeyCount || 0;
    }
    catch (error) {
        console.error('Error listing S3 objects:', error);
    }
    return 0;
}
/**
 * DynamoDB Throttle 메트릭 조회
 */
async function getDynamoDBThrottles(tableName) {
    const endTime = new Date();
    const startTime = new Date(endTime.getTime() - 3600000); // 1시간 전
    let readThrottles = 0;
    let writeThrottles = 0;
    try {
        // Read Throttles
        const readResponse = await cloudwatch.getMetricStatistics({
            Namespace: 'AWS/DynamoDB',
            MetricName: 'ReadThrottleEvents',
            Dimensions: [{ Name: 'TableName', Value: tableName }],
            StartTime: startTime,
            EndTime: endTime,
            Period: 3600,
            Statistics: ['Sum']
        }).promise();
        if (readResponse.Datapoints && readResponse.Datapoints.length > 0) {
            readThrottles = readResponse.Datapoints[0].Sum || 0;
        }
        // Write Throttles
        const writeResponse = await cloudwatch.getMetricStatistics({
            Namespace: 'AWS/DynamoDB',
            MetricName: 'WriteThrottleEvents',
            Dimensions: [{ Name: 'TableName', Value: tableName }],
            StartTime: startTime,
            EndTime: endTime,
            Period: 3600,
            Statistics: ['Sum']
        }).promise();
        if (writeResponse.Datapoints && writeResponse.Datapoints.length > 0) {
            writeThrottles = writeResponse.Datapoints[0].Sum || 0;
        }
    }
    catch (error) {
        console.error(`Error getting DynamoDB throttles for ${tableName}:`, error);
    }
    return { readThrottles, writeThrottles };
}
/**
 * DynamoDB Consumed Capacity 조회
 */
async function getDynamoDBConsumedCapacity(tableName) {
    const endTime = new Date();
    const startTime = new Date(endTime.getTime() - 300000); // 5분 전
    let readCapacity = 0;
    let writeCapacity = 0;
    try {
        // Consumed Read Capacity
        const readResponse = await cloudwatch.getMetricStatistics({
            Namespace: 'AWS/DynamoDB',
            MetricName: 'ConsumedReadCapacityUnits',
            Dimensions: [{ Name: 'TableName', Value: tableName }],
            StartTime: startTime,
            EndTime: endTime,
            Period: 300,
            Statistics: ['Average']
        }).promise();
        if (readResponse.Datapoints && readResponse.Datapoints.length > 0) {
            readCapacity = readResponse.Datapoints[0].Average || 0;
        }
        // Consumed Write Capacity
        const writeResponse = await cloudwatch.getMetricStatistics({
            Namespace: 'AWS/DynamoDB',
            MetricName: 'ConsumedWriteCapacityUnits',
            Dimensions: [{ Name: 'TableName', Value: tableName }],
            StartTime: startTime,
            EndTime: endTime,
            Period: 300,
            Statistics: ['Average']
        }).promise();
        if (writeResponse.Datapoints && writeResponse.Datapoints.length > 0) {
            writeCapacity = writeResponse.Datapoints[0].Average || 0;
        }
    }
    catch (error) {
        console.error(`Error getting DynamoDB consumed capacity for ${tableName}:`, error);
    }
    return { readCapacity, writeCapacity };
}
/**
 * 전체 스토리지 메트릭 집계
 */
async function getStorageMetrics() {
    const metrics = {
        totalStorageSize: 0,
        totalObjectCount: 0,
        totalDynamoDBItems: 0,
        totalDynamoDBSize: 0,
        s3RequestCount: 0,
        dynamoDBRequestCount: 0
    };
    // 이 정보는 위에서 수집한 데이터를 집계하여 반환
    // 실제 구현에서는 캐싱을 고려해야 함
    return metrics;
}
/**
 * 스토리지 이상 징후 감지
 */
function detectStorageIssues(s3Status, dynamoStatus, metrics) {
    const alerts = [];
    // S3 버킷 이슈 확인
    s3Status.forEach(bucket => {
        // 암호화 미설정
        if (bucket.details && bucket.details['Encryption'] === 'Disabled') {
            alerts.push({
                severity: 'warning',
                title: 'S3 Bucket Encryption Disabled',
                message: `Bucket ${bucket.name} does not have encryption enabled. Enable SSE for security.`,
                timestamp: new Date().toISOString()
            });
        }
        // 버저닝 미설정
        if (bucket.details && bucket.details['Versioning'] === 'Disabled') {
            alerts.push({
                severity: 'info',
                title: 'S3 Versioning Disabled',
                message: `Consider enabling versioning for ${bucket.name} to protect against accidental deletion.`,
                timestamp: new Date().toISOString()
            });
        }
        // 큰 버킷 크기
        if (bucket.size > 10 * 1024 * 1024 * 1024) { // 10GB
            alerts.push({
                severity: 'info',
                title: 'Large S3 Bucket',
                message: `Bucket ${bucket.name} is ${formatBytes(bucket.size)}. Consider lifecycle policies for cost optimization.`,
                timestamp: new Date().toISOString()
            });
        }
    });
    // DynamoDB 테이블 이슈 확인
    dynamoStatus.forEach(table => {
        // Throttling 발생
        if (table.throttles && table.throttles > 0) {
            alerts.push({
                severity: table.throttles > 10 ? 'danger' : 'warning',
                title: 'DynamoDB Throttling Detected',
                message: `Table ${table.name} experienced ${table.throttles} throttled requests. Consider increasing capacity or using on-demand mode.`,
                timestamp: new Date().toISOString()
            });
        }
        // 테이블 상태 이상
        if (table.details && table.details['Table Status'] !== 'ACTIVE') {
            alerts.push({
                severity: 'danger',
                title: 'DynamoDB Table Not Active',
                message: `Table ${table.name} is in ${table.details['Table Status']} state.`,
                timestamp: new Date().toISOString()
            });
        }
        // 높은 consumed capacity (프로비전드 모드)
        if (table.metrics && table.details?.['Billing Mode'] === 'PROVISIONED') {
            const consumedRCU = parseFloat(table.metrics['Consumed RCU']);
            const provisionedRCU = table.details['Read Capacity'];
            if (typeof provisionedRCU === 'number' && consumedRCU > provisionedRCU * 0.8) {
                alerts.push({
                    severity: 'warning',
                    title: 'High DynamoDB Read Consumption',
                    message: `Table ${table.name} is using ${consumedRCU}/${provisionedRCU} RCU (${((consumedRCU / provisionedRCU) * 100).toFixed(1)}%).`,
                    timestamp: new Date().toISOString()
                });
            }
        }
    });
    return alerts;
}
/**
 * 바이트를 읽기 쉬운 형식으로 변환
 */
function formatBytes(bytes) {
    if (bytes === 0)
        return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}
//# sourceMappingURL=storage-health.js.map