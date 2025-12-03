# GitNote Enterprise Infrastructure Monitoring System

## 🚀 Overview
AWS 인프라 모니터링 대시보드로, GitNote 프로젝트의 모든 AWS 리소스를 실시간으로 모니터링하고 분석합니다.


### 주요 기능
- **Network Status**: VPC, ALB, NAT Gateway 상태 모니터링
- **Compute Performance**: ECS Fargate 서비스 성능 추적
- **Storage Health**: S3, DynamoDB 상태 및 스로틀링 감지
- **Cost Breakdown**: 서비스별 비용 분석 및 최적화 제안

## 📋 Prerequisites

- AWS CLI configured with appropriate credentials
- Node.js 18.x or higher
- AWS CDK CLI (`npm install -g aws-cdk`)
- TypeScript (`npm install -g typescript`)

## 🛠️ Installation & Deployment

### 1. Clone and Install Dependencies

```bash
cd gitnote-enterprise-monitor
npm install
```

### 2. Configure Your Environment

**필수 설정값 변경:**

1. `index.html` 파일에서 API Gateway URL 설정:
```javascript
// Line 621 in index.html
const API_BASE_URL = 'YOUR_API_GATEWAY_URL'; // 실제 URL로 변경
```

2. `cdk_stack/app.ts` 파일에서 AWS 계정 설정:
```typescript
// Line 20 in app.ts
account: process.env.CDK_DEFAULT_ACCOUNT || 'YOUR_ACCOUNT_ID',
```

3. `lambda_functions/cost-breakdown.ts` 파일에서 계정 ID 설정:
```typescript
// Line 177, 196
Values: ['YOUR_ACCOUNT_ID'] // 실제 계정 ID로 변경
```

### 3. Build the Project

```bash
# TypeScript 컴파일
npm run package

# Lambda 함수 빌드
cd lambda_functions
tsc
cd ..
```

### 4. Deploy to AWS

```bash
# AWS CDK Bootstrap (처음 한 번만)
cdk bootstrap

# 스택 배포
cdk deploy

# 또는 자동 승인과 함께 배포
cdk deploy --require-approval never
```

### 5. Post-Deployment Configuration

배포 완료 후 출력되는 값들을 확인:
```
Outputs:
GitNoteMonitoringStack.APIGatewayURL = https://xxxxx.execute-api.ap-northeast-2.amazonaws.com/prod/
GitNoteMonitoringStack.CloudFrontURL = https://xxxxx.cloudfront.net
GitNoteMonitoringStack.APIKey = xxxxx
```

`index.html` 파일을 업데이트하고 S3에 재배포:
```bash
aws s3 cp index.html s3://gitnote-monitoring-xxx/index.html
aws cloudfront create-invalidation --distribution-id XXXXX --paths "/*"
```

## 🔑 Required IAM Permissions

Lambda 함수가 필요로 하는 최소 권한:

### Read Permissions
- **EC2/VPC**: DescribeVpcs, DescribeSubnets, DescribeNatGateways
- **ELB**: DescribeLoadBalancers, DescribeTargetGroups, DescribeTargetHealth
- **ECS**: DescribeClusters, DescribeServices, DescribeTasks
- **S3**: ListBucket, GetBucketVersioning, GetBucketEncryption
- **DynamoDB**: DescribeTable, DescribeTimeToLive
- **CloudWatch**: GetMetricStatistics, GetMetricData
- **Cost Explorer**: GetCostAndUsage, GetCostForecast (Optional)

## 📊 API Endpoints

### Network Status
```bash
GET /network-status
```
Response:
```json
{
  "resources": [...],
  "alerts": [...],
  "timestamp": "2024-12-01T12:00:00Z"
}
```

### Compute Performance
```bash
GET /compute-performance
```
Response:
```json
{
  "services": [...],
  "metrics": {...},
  "avgResponseTime": 18,
  "alerts": [...]
}
```

### Storage Health
```bash
GET /storage-health
```
Response:
```json
{
  "storage": [...],
  "metrics": {...},
  "alerts": [...]
}
```

### Cost Breakdown
```bash
GET /cost-breakdown
```
Response:
```json
{
  "services": [...],
  "totalCost": 415.94,
  "costTrend": 8.7,
  "recommendations": [...]
}
```

## 🎨 Frontend Features

### Real-time Monitoring
- Auto-refresh every 60 seconds
- Loading states with spinners
- Error handling with user-friendly messages

### Visual Analytics
- Performance charts using Chart.js
- Cost breakdown doughnut chart
- Time-series CPU/Memory utilization

### Alert System
- Color-coded severity levels (success/warning/danger)
- Actionable recommendations
- Threshold-based alerting

## 🧪 Testing

### Local Testing (Lambda Functions)
```bash
# Install test dependencies
npm install --save-dev @types/jest jest ts-jest

# Run tests
npm test
```

### Manual API Testing
```bash
# Test individual endpoints
curl https://YOUR_API_GATEWAY_URL/network-status \
  -H "x-api-key: YOUR_API_KEY"
```

## 📈 Monitoring the Monitor

### CloudWatch Logs
- Lambda function logs: `/aws/lambda/GitNote*`
- API Gateway logs: `API-Gateway-Execution-Logs_xxx/prod`

### CloudWatch Metrics
- Lambda invocations and errors
- API Gateway 4xx/5xx errors
- Lambda duration and throttles

## 🔧 Troubleshooting

### Common Issues

1. **CORS Errors**
   - Ensure API Gateway CORS is properly configured
   - Check `Access-Control-Allow-Origin` headers

2. **Permission Denied**
   - Verify Lambda execution role has all required permissions
   - Check resource ARNs in IAM policies

3. **Cost Explorer Access Denied**
   - Cost Explorer API requires specific IAM permissions
   - Falls back to simulated data if permissions are missing

4. **No Data Displayed**
   - Verify resource names match exactly (case-sensitive)
   - Check CloudWatch Logs for Lambda errors
   - Ensure resources exist in the correct region

## 🚨 Security Best Practices

1. **API Key Management**
   - Rotate API keys regularly
   - Use AWS Secrets Manager for sensitive data
   - Never commit API keys to source control

2. **Least Privilege Access**
   - Lambda functions have read-only permissions
   - Specific resource ARNs where possible
   - No wildcard permissions for write operations

3. **Network Security**
   - CloudFront HTTPS only
   - S3 bucket not publicly accessible
   - API Gateway throttling enabled

## 📝 Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  CloudFront │────▶│  S3 Bucket   │     │   Lambda    │
│    (CDN)    │     │  (Frontend)  │     │  Functions  │
└─────────────┘     └──────────────┘     └─────────────┘
                            │                     ▲
                            ▼                     │
                    ┌──────────────┐              │
                    │ API Gateway  │──────────────┘
                    └──────────────┘
                            │
                    ┌──────────────┐
                    │   AWS APIs   │
                    │ (EC2, ECS,   │
                    │  S3, DDB...)  │
                    └──────────────┘
<<<<<<< HEAD
=======


