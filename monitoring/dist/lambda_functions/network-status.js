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
const ec2 = new AWS.EC2({ region: 'ap-northeast-2' });
const elbv2 = new AWS.ELBv2({ region: 'ap-northeast-2' });
/**
 * GitNote Network Status Lambda Handler
 * VPC, Subnets, NAT Gateway, ALB 상태를 동적으로 조회
 */
const handler = async (event) => {
    console.log('Network Status Request:', JSON.stringify(event));
    try {
        // 병렬로 네트워크 리소스 조회
        const [vpcData, albData, natGatewayData] = await Promise.all([
            getVPCStatus(),
            getALBStatus(),
            getNATGatewayStatus()
        ]);
        // 이상 징후 감지
        const alerts = detectNetworkIssues(vpcData, albData, natGatewayData);
        // 응답 구성
        const response = {
            resources: [
                ...vpcData,
                ...albData,
                ...natGatewayData
            ],
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
        console.error('Error in network-status handler:', error);
        return {
            statusCode: 500,
            headers: {
                'Content-Type': 'application/json',
                'Access-Control-Allow-Origin': '*'
            },
            body: JSON.stringify({
                error: 'Failed to retrieve network status',
                message: error instanceof Error ? error.message : 'Unknown error',
                timestamp: new Date().toISOString()
            })
        };
    }
};
exports.handler = handler;
/**
 * VPC 및 서브넷 상태 조회
 */
async function getVPCStatus() {
    const resources = [];
    try {
        // VPC 조회 - Name 태그로 필터링
        const vpcs = await ec2.describeVpcs({
            Filters: [
                {
                    Name: 'tag:Name',
                    Values: ['gitnote-vpc']
                }
            ]
        }).promise();
        if (vpcs.Vpcs && vpcs.Vpcs.length > 0) {
            const vpc = vpcs.Vpcs[0];
            // 서브넷 조회
            const subnets = await ec2.describeSubnets({
                Filters: [
                    {
                        Name: 'vpc-id',
                        Values: [vpc.VpcId]
                    }
                ]
            }).promise();
            // Internet Gateway 조회
            const igws = await ec2.describeInternetGateways({
                Filters: [
                    {
                        Name: 'attachment.vpc-id',
                        Values: [vpc.VpcId]
                    }
                ]
            }).promise();
            resources.push({
                name: 'gitnote-vpc',
                type: 'VPC',
                status: vpc.State === 'available' ? 'healthy' : 'warning',
                details: {
                    'VPC ID': vpc.VpcId,
                    'CIDR Block': vpc.CidrBlock,
                    'Subnets': subnets.Subnets?.length || 0,
                    'Internet Gateway': igws.InternetGateways?.length > 0 ? 'Attached' : 'Not Attached',
                    'State': vpc.State
                }
            });
            // 서브넷 상태 확인
            const publicSubnets = subnets.Subnets?.filter(s => s.MapPublicIpOnLaunch === true).length || 0;
            const privateSubnets = (subnets.Subnets?.length || 0) - publicSubnets;
            if (publicSubnets > 0) {
                resources.push({
                    name: 'Public Subnets',
                    type: 'Subnet',
                    status: 'healthy',
                    details: {
                        'Count': publicSubnets,
                        'Auto-assign Public IP': 'Enabled'
                    }
                });
            }
            if (privateSubnets > 0) {
                resources.push({
                    name: 'Private Subnets',
                    type: 'Subnet',
                    status: 'healthy',
                    details: {
                        'Count': privateSubnets,
                        'Type': 'Private'
                    }
                });
            }
        }
    }
    catch (error) {
        console.error('Error getting VPC status:', error);
        resources.push({
            name: 'gitnote-vpc',
            type: 'VPC',
            status: 'critical',
            details: {
                'Error': 'Failed to retrieve VPC information'
            }
        });
    }
    return resources;
}
/**
 * Application Load Balancer 상태 조회
 */
async function getALBStatus() {
    const resources = [];
    try {
        // ALB 조회 - Name으로 필터링
        const loadBalancers = await elbv2.describeLoadBalancers({
            Names: ['gitnote-alb']
        }).promise();
        if (loadBalancers.LoadBalancers && loadBalancers.LoadBalancers.length > 0) {
            const alb = loadBalancers.LoadBalancers[0];
            // Target Group 조회
            const targetGroups = await elbv2.describeTargetGroups({
                LoadBalancerArn: alb.LoadBalancerArn
            }).promise();
            // Target Health 조회
            let healthyTargets = 0;
            let unhealthyTargets = 0;
            if (targetGroups.TargetGroups && targetGroups.TargetGroups.length > 0) {
                for (const tg of targetGroups.TargetGroups) {
                    const health = await elbv2.describeTargetHealth({
                        TargetGroupArn: tg.TargetGroupArn
                    }).promise();
                    health.TargetHealthDescriptions?.forEach(thd => {
                        if (thd.TargetHealth?.State === 'healthy') {
                            healthyTargets++;
                        }
                        else {
                            unhealthyTargets++;
                        }
                    });
                }
            }
            const albStatus = alb.State?.Code === 'active' && healthyTargets > 0 ? 'healthy' :
                unhealthyTargets > 0 ? 'warning' : 'critical';
            resources.push({
                name: 'gitnote-alb',
                type: 'Application Load Balancer',
                status: albStatus,
                details: {
                    'DNS Name': alb.DNSName,
                    'State': alb.State?.Code,
                    'Type': alb.Type,
                    'Scheme': alb.Scheme,
                    'Availability Zones': alb.AvailabilityZones?.length || 0,
                    'Healthy Targets': healthyTargets,
                    'Unhealthy Targets': unhealthyTargets
                }
            });
            // Target Group 정보
            if (targetGroups.TargetGroups && targetGroups.TargetGroups.length > 0) {
                const tg = targetGroups.TargetGroups.find(t => t.TargetGroupName === 'gitnote-tg') || targetGroups.TargetGroups[0];
                resources.push({
                    name: tg.TargetGroupName || 'Target Group',
                    type: 'Target Group',
                    status: healthyTargets > 0 ? 'healthy' : 'critical',
                    details: {
                        'Protocol': tg.Protocol,
                        'Port': tg.Port,
                        'Target Type': tg.TargetType,
                        'Health Check Path': tg.HealthCheckPath || '/',
                        'Healthy/Total': `${healthyTargets}/${healthyTargets + unhealthyTargets}`
                    }
                });
            }
        }
    }
    catch (error) {
        console.error('Error getting ALB status:', error);
        // ALB가 없거나 접근 불가한 경우
        if (error.code === 'LoadBalancerNotFound') {
            resources.push({
                name: 'gitnote-alb',
                type: 'Application Load Balancer',
                status: 'warning',
                details: {
                    'Status': 'Not Found',
                    'Message': 'ALB not deployed or name mismatch'
                }
            });
        }
        else {
            resources.push({
                name: 'gitnote-alb',
                type: 'Application Load Balancer',
                status: 'critical',
                details: {
                    'Error': 'Failed to retrieve ALB information'
                }
            });
        }
    }
    return resources;
}
/**
 * NAT Gateway 상태 조회
 */
async function getNATGatewayStatus() {
    const resources = [];
    try {
        // NAT Gateway 조회
        const natGateways = await ec2.describeNatGateways({
            Filter: [
                {
                    Name: 'state',
                    Values: ['available', 'pending', 'failed']
                }
            ]
        }).promise();
        if (natGateways.NatGateways && natGateways.NatGateways.length > 0) {
            natGateways.NatGateways.forEach(nat => {
                const status = nat.State === 'available' ? 'healthy' :
                    nat.State === 'pending' ? 'warning' : 'critical';
                resources.push({
                    name: `NAT Gateway (${nat.SubnetId})`,
                    type: 'NAT Gateway',
                    status,
                    details: {
                        'NAT Gateway ID': nat.NatGatewayId,
                        'State': nat.State,
                        'Public IP': nat.NatGatewayAddresses?.[0]?.PublicIp || 'N/A',
                        'Private IP': nat.NatGatewayAddresses?.[0]?.PrivateIp || 'N/A',
                        'Subnet': nat.SubnetId
                    }
                });
            });
        }
        else {
            resources.push({
                name: 'NAT Gateway',
                type: 'NAT Gateway',
                status: 'warning',
                details: {
                    'Status': 'Not Found',
                    'Message': 'No NAT Gateway configured'
                }
            });
        }
    }
    catch (error) {
        console.error('Error getting NAT Gateway status:', error);
    }
    return resources;
}
/**
 * 네트워크 이상 징후 감지
 */
function detectNetworkIssues(vpcData, albData, natData) {
    const alerts = [];
    // VPC 이슈 확인
    const vpcIssue = vpcData.find(r => r.status !== 'healthy');
    if (vpcIssue) {
        alerts.push({
            severity: 'danger',
            title: 'VPC Configuration Issue',
            message: `VPC ${vpcIssue.name} is in ${vpcIssue.status} state. Check network configuration.`,
            timestamp: new Date().toISOString()
        });
    }
    // ALB 타겟 헬스 체크
    const albResource = albData.find(r => r.type === 'Application Load Balancer');
    if (albResource && albResource.details['Unhealthy Targets'] > 0) {
        alerts.push({
            severity: 'warning',
            title: 'Unhealthy ALB Targets Detected',
            message: `${albResource.details['Unhealthy Targets']} unhealthy targets in ALB. Services may be experiencing issues.`,
            timestamp: new Date().toISOString()
        });
    }
    // NAT Gateway 이슈 확인
    const natIssue = natData.find(r => r.status === 'critical');
    if (natIssue) {
        alerts.push({
            severity: 'danger',
            title: 'NAT Gateway Failure',
            message: 'NAT Gateway is in failed state. Private subnets cannot access internet.',
            timestamp: new Date().toISOString()
        });
    }
    return alerts;
}
//# sourceMappingURL=network-status.js.map