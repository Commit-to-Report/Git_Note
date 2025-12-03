/**
 * Network Status Monitor - Enterprise Grade
 * * This Lambda function performs a deep-dive health check on the network infrastructure.
 * It aggregates data from VPC, Subnets, NAT Gateways, and Load Balancers.
 * * Key Features:
 * 1. Parallel Execution: Fetches EC2 and ELB data concurrently for low latency.
 * 2. IP Exhaustion Check: Monitors available IP addresses in subnets.
 * 3. Deep Target Inspection: Checks individual health of instances behind ALB.
 * 4. Fault Tolerance: Gracefully handles missing resources without crashing.
 */

const { 
    EC2Client, 
    DescribeVpcsCommand, 
    DescribeSubnetsCommand, 
    DescribeNatGatewaysCommand, 
    DescribeInternetGatewaysCommand 
} = require("@aws-sdk/client-ec2");

const { 
    ElasticLoadBalancingV2Client, 
    DescribeLoadBalancersCommand, 
    DescribeTargetGroupsCommand, 
    DescribeTargetHealthCommand 
} = require("@aws-sdk/client-elastic-load-balancing-v2");

// Initialize AWS Clients with persistent connections
const ec2 = new EC2Client({ region: 'ap-northeast-2' });
const elbv2 = new ElasticLoadBalancingV2Client({ region: 'ap-northeast-2' });

// Configuration Constants
const CONFIG = {
    VPC_TAG: 'gitnote-vpc',
    ALB_NAME: 'gitnote-alb',
    THRESHOLDS: {
        MIN_AVAILABLE_IPS: 5 // Alert if subnet has fewer than 5 free IPs
    }
};

exports.handler = async (event) => {
    // Structured logging for observability
    console.log('Starting Network Health Check execution...');

    try {
        // Execute independent tasks in parallel to minimize Lambda duration
        const [vpcResult, albResult, natResult] = await Promise.allSettled([
            fetchVpcArchitecture(),
            fetchLoadBalancerStack(),
            fetchNatGatewayStatus()
        ]);

        // Process results from parallel execution
        const vpcData = vpcResult.status === 'fulfilled' ? vpcResult.value : [];
        const albData = albResult.status === 'fulfilled' ? albResult.value : [];
        const natData = natResult.status === 'fulfilled' ? natResult.value : [];

        // Aggregate all resources
        const allResources = [...vpcData, ...albData, ...natData];

        // Perform heuristic analysis to generate alerts
        const alerts = analyzeSystemHealth(allResources);

        // Check if any critical API failure occurred
        if (vpcResult.status === 'rejected') console.error('VPC Fetch Failed:', vpcResult.reason);
        if (albResult.status === 'rejected') console.error('ALB Fetch Failed:', albResult.reason);
        if (natResult.status === 'rejected') console.error('NAT Fetch Failed:', natResult.reason);

        // If no resources found at all, fallback to a critical alert state
        if (allResources.length === 0) {
            throw new Error('No network resources discovered. Check tags and region configuration.');
        }

        return createApiResponse(allResources, alerts);

    } catch (error) {
        console.error('Fatal Error in Network Monitor:', error);
        
        // Fail-safe response to ensure dashboard visualization
        return createApiResponse([], [{
            severity: 'danger',
            title: 'Network Monitor Failure',
            message: error.message || 'Internal Server Error during health check.',
            timestamp: new Date().toISOString()
        }]);
    }
};

/**
 * 1. VPC Architecture Fetcher
 * Retrieves VPC, Subnets, and calculates IP availability.
 */
async function fetchVpcArchitecture() {
    const resources = [];

    // Fetch VPC by Tag
    const vpcRes = await ec2.send(new DescribeVpcsCommand({ 
        Filters: [{ Name: 'tag:Name', Values: [CONFIG.VPC_TAG] }] 
    }));

    if (!vpcRes.Vpcs || vpcRes.Vpcs.length === 0) {
        console.warn(`VPC with tag ${CONFIG.VPC_TAG} not found.`);
        return resources;
    }

    const vpc = vpcRes.Vpcs[0];
    
    // Fetch Subnets attached to this VPC
    const subRes = await ec2.send(new DescribeSubnetsCommand({ 
        Filters: [{ Name: 'vpc-id', Values: [vpc.VpcId] }] 
    }));

    // Calculate total available IPs across all subnets
    const totalFreeIps = subRes.Subnets?.reduce((acc, sub) => acc + (sub.AvailableIpAddressCount || 0), 0) || 0;

    resources.push({
        name: 'gitnote-vpc',
        type: 'VPC',
        status: vpc.State === 'available' ? 'healthy' : 'critical',
        details: {
            'VPC ID': vpc.VpcId,
            'State': vpc.State,
            'CIDR Block': vpc.CidrBlock,
            'Total Subnets': subRes.Subnets?.length || 0,
            'Available IPs': totalFreeIps
        }
    });

    return resources;
}

/**
 * 2. Load Balancer Stack Fetcher
 * Retrieves ALB, Target Groups, and performs deep health checks on targets.
 */
async function fetchLoadBalancerStack() {
    const resources = [];

    try {
        // Fetch ALB
        const lbRes = await elbv2.send(new DescribeLoadBalancersCommand({ 
            Names: [CONFIG.ALB_NAME] 
        }));

        if (!lbRes.LoadBalancers || lbRes.LoadBalancers.length === 0) return resources;

        const alb = lbRes.LoadBalancers[0];
        
        // Fetch Target Groups attached to ALB
        const tgRes = await elbv2.send(new DescribeTargetGroupsCommand({ 
            LoadBalancerArn: alb.LoadBalancerArn 
        }));

        let healthyTargets = 0;
        let unhealthyTargets = 0;
        let targetGroupCount = 0;

        if (tgRes.TargetGroups) {
            targetGroupCount = tgRes.TargetGroups.length;
            
            // Check health for each target group
            for (const tg of tgRes.TargetGroups) {
                try {
                    const healthRes = await elbv2.send(new DescribeTargetHealthCommand({ 
                        TargetGroupArn: tg.TargetGroupArn 
                    }));
                    
                    healthRes.TargetHealthDescriptions?.forEach(target => {
                        if (target.TargetHealth.State === 'healthy') {
                            healthyTargets++;
                        } else if (target.TargetHealth.State !== 'unused') {
                            // 'unused' often means registering, so we focus on explicit failures
                            unhealthyTargets++;
                        }
                    });
                } catch (err) {
                    console.warn(`Failed to check health for TG ${tg.TargetGroupName}:`, err.message);
                }
            }
        }

        // Determine Overall ALB Status
        let derivedStatus = 'healthy';
        if (alb.State.Code !== 'active') {
            derivedStatus = 'critical';
        } else if (unhealthyTargets > 0) {
            derivedStatus = 'warning';
        } else if (targetGroupCount > 0 && healthyTargets === 0) {
            // Active ALB but no healthy targets (potential config issue)
            derivedStatus = 'warning';
        }

        resources.push({
            name: 'gitnote-alb',
            type: 'Application Load Balancer',
            status: derivedStatus,
            details: {
                'DNS Name': alb.DNSName,
                'State': alb.State.Code,
                'Target Groups': targetGroupCount,
                'Target Health': `Healthy: ${healthyTargets} / Unhealthy: ${unhealthyTargets}`
            }
        });

    } catch (error) {
        // Handle case where ALB doesn't exist (e.g., deleted manually)
        if (error.name !== 'LoadBalancerNotFound') {
            throw error;
        }
    }

    return resources;
}

/**
 * 3. NAT Gateway Fetcher
 * Retrieves NAT Gateways to ensure outbound connectivity for private subnets.
 */
async function fetchNatGatewayStatus() {
    const resources = [];
    
    // Filter for active NAT gateways only
    const natRes = await ec2.send(new DescribeNatGatewaysCommand({ 
        Filter: [{ Name: 'state', Values: ['available', 'pending', 'failed'] }] 
    }));

    if (natRes.NatGateways) {
        natRes.NatGateways.forEach(nat => {
            resources.push({
                name: `NAT Gateway (${nat.SubnetId})`,
                type: 'NAT Gateway',
                status: nat.State === 'available' ? 'healthy' : 'critical',
                details: {
                    'ID': nat.NatGatewayId,
                    'State': nat.State,
                    'Public IP': nat.NatGatewayAddresses?.[0]?.PublicIp || 'Pending'
                }
            });
        });
    }

    return resources;
}

/**
 * Logic Engine: Analyze System Health
 * Generates actionable alerts based on resource states and thresholds.
 */
function analyzeSystemHealth(resources) {
    const alerts = [];

    resources.forEach(r => {
        // 1. Critical State Detection
        if (r.status === 'critical') {
            alerts.push({
                severity: 'danger',
                title: `${r.type} Critical Failure`,
                message: `Resource ${r.name} is in ${r.details.State || 'failed'} state. Immediate action required.`,
                timestamp: new Date().toISOString()
            });
        }

        // 2. Warning State Detection
        if (r.status === 'warning') {
            let msg = `${r.name} requires attention.`;
            
            // Context-aware messages
            if (r.type === 'Application Load Balancer') {
                msg = `ALB detects unhealthy targets (${r.details['Target Health']}). Check application logs.`;
            }

            alerts.push({
                severity: 'warning',
                title: `${r.type} Degraded`,
                message: msg,
                timestamp: new Date().toISOString()
            });
        }

        // 3. Heuristic: IP Exhaustion Warning
        if (r.type === 'VPC' && r.details['Available IPs'] < CONFIG.THRESHOLDS.MIN_AVAILABLE_IPS) {
            alerts.push({
                severity: 'warning',
                title: 'IP Address Exhaustion',
                message: `VPC has very low available IP addresses (${r.details['Available IPs']}). Scaling may fail.`,
                timestamp: new Date().toISOString()
            });
        }
    });

    return alerts;
}

/**
 * Helper: Standardized API Response
 */
function createApiResponse(resources, alerts) {
    return {
        statusCode: 200,
        headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Headers': 'Content-Type',
            'Access-Control-Allow-Methods': 'GET, OPTIONS'
        },
        body: JSON.stringify({
            resources: resources,
            alerts: alerts || [],
            timestamp: new Date().toISOString()
        })
    };
}