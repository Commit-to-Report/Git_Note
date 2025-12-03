#!/usr/bin/env node
// cdk_stack/app.ts
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { GitNoteMonitoringStack } from './gitnote-monitoring-stack';

/**
 * GitNote Monitoring CDK Application
 * 
 * 배포 방법:
 * 1. npm install
 * 2. npm run build
 * 3. cdk bootstrap (처음 한 번만)
 * 4. cdk deploy
 */
const app = new cdk.App();

// 스택 생성
new GitNoteMonitoringStack(app, 'GitNoteMonitoringStack', {
    /* 
     * 계정/리전 설정
     * env 설정을 명시적으로 하거나 AWS CLI 프로필 사용
     */
    env: {
        account: process.env.CDK_DEFAULT_ACCOUNT || '061039804626',
        region: process.env.CDK_DEFAULT_REGION || 'ap-northeast-2',
    },
    
    description: 'GitNote Enterprise Monitoring Dashboard Infrastructure',
    
    /* 스택 레벨 태그 */
    tags: {
        'Project': 'GitNote',
        'Owner': 'DevOps Team',
        'CostCenter': 'Engineering',
        'Environment': 'Production'
    }
});

app.synth();