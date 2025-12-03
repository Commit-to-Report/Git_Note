#!/usr/bin/env node
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
// cdk_stack/app.ts
require("source-map-support/register");
const cdk = __importStar(require("aws-cdk-lib"));
const gitnote_monitoring_stack_1 = require("./gitnote-monitoring-stack");
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
new gitnote_monitoring_stack_1.GitNoteMonitoringStack(app, 'GitNoteMonitoringStack', {
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
//# sourceMappingURL=app.js.map