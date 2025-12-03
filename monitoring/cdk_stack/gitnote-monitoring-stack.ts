// cdk_stack/gitnote-monitoring-stack.ts
import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';

/**
 * GitNote Enterprise Monitoring Stack
 * 완전한 모니터링 인프라를 AWS에 배포
 */
export class GitNoteMonitoringStack extends cdk.Stack {
    constructor(scope: Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);

        // ========================================
        // 1. Lambda 실행 역할 생성 (최소 권한 원칙)
        // ========================================
        
        const lambdaRole = new iam.Role(this, 'GitNoteMonitoringLambdaRole', {
            assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
            description: 'Execution role for GitNote monitoring Lambda functions',
            managedPolicies: [
                iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaBasicExecutionRole')
            ]
        });

        // EC2/VPC 읽기 권한
        lambdaRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                'ec2:DescribeVpcs',
                'ec2:DescribeSubnets',
                'ec2:DescribeInternetGateways',
                'ec2:DescribeNatGateways',
                'ec2:DescribeRouteTables',
                'ec2:DescribeSecurityGroups',
                'ec2:DescribeInstances'
            ],
            resources: ['*']
        }));

        // ELB/ALB 읽기 권한
        lambdaRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                'elasticloadbalancing:DescribeLoadBalancers',
                'elasticloadbalancing:DescribeTargetGroups',
                'elasticloadbalancing:DescribeTargetHealth',
                'elasticloadbalancing:DescribeListeners',
                'elasticloadbalancing:DescribeRules'
            ],
            resources: ['*']
        }));

        // ECS 읽기 권한
        lambdaRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                'ecs:DescribeClusters',
                'ecs:DescribeServices',
                'ecs:DescribeTasks',
                'ecs:ListTasks',
                'ecs:DescribeTaskDefinition',
                'ecs:DescribeContainerInstances',
                'ecs:ListServices'
            ],
            resources: ['*']
        }));

        // S3 읽기 권한 (특정 버킷만)
        lambdaRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                's3:ListBucket',
                's3:GetBucketLocation',
                's3:GetBucketVersioning',
                's3:GetBucketEncryption',
                's3:GetBucketLifecycleConfiguration',
                's3:GetBucketLogging',
                's3:ListBucketVersions'
            ],
            resources: [
                'arn:aws:s3:::gitnote-s3-bucket',
                'arn:aws:s3:::gitnote-s3-bucket/*'
            ]
        }));

        // DynamoDB 읽기 권한 (특정 테이블만)
        lambdaRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                'dynamodb:DescribeTable',
                'dynamodb:DescribeTimeToLive',
                'dynamodb:ListTagsOfResource',
                'dynamodb:DescribeContinuousBackups',
                'dynamodb:DescribeGlobalTable',
                'dynamodb:DescribeTableReplicaAutoScaling'
            ],
            resources: [
                `arn:aws:dynamodb:${this.region}:${this.account}:table/UserPreset`,
                `arn:aws:dynamodb:${this.region}:${this.account}:table/UserReports`
            ]
        }));

        // CloudWatch 메트릭 읽기 권한
        lambdaRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                'cloudwatch:GetMetricStatistics',
                'cloudwatch:GetMetricData',
                'cloudwatch:ListMetrics',
                'cloudwatch:DescribeAlarms'
            ],
            resources: ['*']
        }));

        // Cost Explorer 읽기 권한 (선택적)
        lambdaRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                'ce:GetCostAndUsage',
                'ce:GetCostForecast',
                'ce:GetDimensionValues',
                'ce:GetReservationUtilization',
                'ce:GetSavingsPlansPurchaseRecommendation'
            ],
            resources: ['*']
        }));

        // Secrets Manager 읽기 권한 (특정 시크릿만)
        lambdaRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                'secretsmanager:DescribeSecret',
                'secretsmanager:ListSecretVersionIds'
            ],
            resources: [
                `arn:aws:secretsmanager:${this.region}:${this.account}:secret:GitNote/Secrets-*`
            ]
        }));

        // ========================================
        // 2. Lambda 함수 생성
        // ========================================

        // Lambda Layer (공통 의존성)
        /*
        const dependenciesLayer = new lambda.LayerVersion(this, 'MonitoringDependencies', {
            code: lambda.Code.fromAsset('lambda_layer'),
            compatibleRuntimes: [lambda.Runtime.NODEJS_18_X],
            description: 'Common dependencies for monitoring functions'
        });
        */

        // Network Status Lambda
        const networkStatusLambda = new lambda.Function(this, 'NetworkStatusFunction', {
            runtime: lambda.Runtime.NODEJS_18_X,
            handler: 'network-status.handler',
            code: lambda.Code.fromAsset('./lambda_functions'),
            role: lambdaRole,
            timeout: cdk.Duration.seconds(30),
            memorySize: 512,
            environment: {
                REGION: this.region,
                NODE_ENV: 'production'
            },
            /*layers: [dependenciesLayer],*/ // <- 삭제 또는 주석 처리했음
            logRetention: logs.RetentionDays.ONE_WEEK,
            description: 'Monitor VPC, ALB, and network resources'
        });

        // Compute Performance Lambda
        const computePerformanceLambda = new lambda.Function(this, 'ComputePerformanceFunction', {
            runtime: lambda.Runtime.NODEJS_18_X,
            handler: 'compute-performance.handler',
            code: lambda.Code.fromAsset('./lambda_functions'),
            role: lambdaRole,
            timeout: cdk.Duration.seconds(30),
            memorySize: 512,
            environment: {
                REGION: this.region,
                NODE_ENV: 'production'
            },
            /*layers: [dependenciesLayer],*/ // <- 삭제 또는 주석 처리했음
            logRetention: logs.RetentionDays.ONE_WEEK,
            description: 'Monitor ECS Fargate performance metrics'
        });

        // Storage Health Lambda
        const storageHealthLambda = new lambda.Function(this, 'StorageHealthFunction', {
            runtime: lambda.Runtime.NODEJS_18_X,
            handler: 'storage-health.handler',
            code: lambda.Code.fromAsset('./lambda_functions'),
            role: lambdaRole,
            timeout: cdk.Duration.seconds(30),
            memorySize: 512,
            environment: {
                REGION: this.region,
                NODE_ENV: 'production'
            },
            /*layers: [dependenciesLayer],*/ // <- 삭제 또는 주석 처리했음
            logRetention: logs.RetentionDays.ONE_WEEK,
            description: 'Monitor S3 and DynamoDB health'
        });

        // Cost Breakdown Lambda
        const costBreakdownLambda = new lambda.Function(this, 'CostBreakdownFunction', {
            runtime: lambda.Runtime.NODEJS_18_X,
            handler: 'cost-breakdown.handler',
            code: lambda.Code.fromAsset('./lambda_functions'),
            role: lambdaRole,
            timeout: cdk.Duration.seconds(30),
            memorySize: 512,
            environment: {
                REGION: this.region,
                ACCOUNT_ID: this.account,
                NODE_ENV: 'production'
            },
            /*layers: [dependenciesLayer],*/ // <- 삭제 또는 주석 처리했음
            logRetention: logs.RetentionDays.ONE_WEEK,
            description: 'Analyze AWS service costs and provide recommendations'
        });

        // ========================================
        // 3. API Gateway 설정
        // ========================================

        const api = new apigateway.RestApi(this, 'GitNoteMonitoringAPI', {
            restApiName: 'GitNote Monitoring API',
            description: 'API for GitNote infrastructure monitoring dashboard',
            deployOptions: {
                stageName: 'prod',
                loggingLevel: apigateway.MethodLoggingLevel.INFO,
                dataTraceEnabled: true,
                tracingEnabled: true,
                throttlingBurstLimit: 100,
                throttlingRateLimit: 50
            },
            defaultCorsPreflightOptions: {
                allowOrigins: apigateway.Cors.ALL_ORIGINS,
                allowMethods: apigateway.Cors.ALL_METHODS,
                allowHeaders: [
                    'Content-Type',
                    'X-Amz-Date',
                    'Authorization',
                    'X-Api-Key',
                    'X-Amz-Security-Token'
                ]
            }
        });

        // API 엔드포인트 생성
        const networkStatus = api.root.addResource('network-status');
        networkStatus.addMethod('GET', new apigateway.LambdaIntegration(networkStatusLambda));

        const computePerformance = api.root.addResource('compute-performance');
        computePerformance.addMethod('GET', new apigateway.LambdaIntegration(computePerformanceLambda));

        const storageHealth = api.root.addResource('storage-health');
        storageHealth.addMethod('GET', new apigateway.LambdaIntegration(storageHealthLambda));

        const costBreakdown = api.root.addResource('cost-breakdown');
        costBreakdown.addMethod('GET', new apigateway.LambdaIntegration(costBreakdownLambda));

        // API Key (선택적)
        const apiKey = api.addApiKey('GitNoteMonitoringAPIKey', {
            description: 'API key for GitNote monitoring dashboard',
            apiKeyName: 'gitnote-monitoring-key'
        });

        const usagePlan = api.addUsagePlan('GitNoteMonitoringUsagePlan', {
            name: 'Standard',
            throttle: {
                rateLimit: 100,
                burstLimit: 200
            },
            quota: {
                limit: 10000,
                period: apigateway.Period.DAY
            }
        });

        usagePlan.addApiKey(apiKey);
        usagePlan.addApiStage({
            api: api,
            stage: api.deploymentStage
        });

        // ========================================
        // 4. Frontend 호스팅 (S3 + CloudFront)
        // ========================================

        // S3 버킷 (정적 웹사이트 호스팅)
        const websiteBucket = new s3.Bucket(this, 'GitNoteMonitoringWebsite', {
            bucketName: `gitnote-monitoring-${this.account}-${this.region}`,
            websiteIndexDocument: 'index.html',
            websiteErrorDocument: 'error.html',
            publicReadAccess: false,
            blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
            removalPolicy: cdk.RemovalPolicy.DESTROY,
            autoDeleteObjects: true,
            cors: [
                {
                    allowedMethods: [
                        s3.HttpMethods.GET,
                        s3.HttpMethods.HEAD
                    ],
                    allowedOrigins: ['*'],
                    allowedHeaders: ['*']
                }
            ]
        });

        // CloudFront Origin Access Identity
        const originAccessIdentity = new cloudfront.OriginAccessIdentity(this, 'OAI', {
            comment: 'OAI for GitNote Monitoring Dashboard'
        });

        websiteBucket.grantRead(originAccessIdentity);

        // CloudFront Distribution
        const distribution = new cloudfront.Distribution(this, 'GitNoteMonitoringDistribution', {
            defaultRootObject: 'index.html',
            defaultBehavior: {
                origin: new origins.S3Origin(websiteBucket, {
                    originAccessIdentity: originAccessIdentity
                }),
                viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
                cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
                compress: true
            },
            errorResponses: [
                {
                    httpStatus: 404,
                    responseHttpStatus: 200,
                    responsePagePath: '/index.html',
                    ttl: cdk.Duration.minutes(5)
                }
            ],
            comment: 'CloudFront distribution for GitNote Monitoring Dashboard',
            priceClass: cloudfront.PriceClass.PRICE_CLASS_100
        });

        // S3 배포 (Frontend 파일)
        new s3deploy.BucketDeployment(this, 'DeployWebsite', {
            sources: [s3deploy.Source.asset('./frontend')],
            destinationBucket: websiteBucket,
            distribution,
            distributionPaths: ['/*']
        });

        // ========================================
        // 5. Outputs
        // ========================================

        new cdk.CfnOutput(this, 'APIGatewayURL', {
            value: api.url,
            description: 'API Gateway endpoint URL',
            exportName: 'GitNoteMonitoringAPIURL'
        });

        new cdk.CfnOutput(this, 'APIKey', {
            value: apiKey.keyId,
            description: 'API Key ID (retrieve value from console)',
            exportName: 'GitNoteMonitoringAPIKey'
        });

        new cdk.CfnOutput(this, 'CloudFrontURL', {
            value: `https://${distribution.distributionDomainName}`,
            description: 'CloudFront distribution URL for the monitoring dashboard',
            exportName: 'GitNoteMonitoringDashboardURL'
        });

        new cdk.CfnOutput(this, 'S3BucketName', {
            value: websiteBucket.bucketName,
            description: 'S3 bucket name for website hosting',
            exportName: 'GitNoteMonitoringBucket'
        });

        // Tags
        cdk.Tags.of(this).add('Project', 'GitNote');
        cdk.Tags.of(this).add('Component', 'Monitoring');
        cdk.Tags.of(this).add('Environment', 'Production');
        cdk.Tags.of(this).add('ManagedBy', 'CDK');
    }
}