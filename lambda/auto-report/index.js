const { DynamoDBClient, ScanCommand } = require("@aws-sdk/client-dynamodb");

/**
 * AWS Lambda Handler for Automatic Report Generation
 *
 * EventBridge에서 frequency별로 호출됩니다:
 * - DAILY: 매일 실행
 * - WEEKLY: 매주 월요일 실행
 * - MONTHLY: 매월 1일 실행
 */

// 환경 변수
const USER_PRESET_TABLE = process.env.USER_PRESET_TABLE;
const BACKEND_API_URL = process.env.BACKEND_API_URL;
const AWS_REGION = process.env.AWS_REGION || "ap-northeast-2";

// DynamoDB 클라이언트
const dynamoDbClient = new DynamoDBClient({ region: AWS_REGION });

exports.handler = async (event) => {
  console.log("[AutoReportHandler] Starting automatic report generation");
  console.log("[AutoReportHandler] Event:", JSON.stringify(event));

  // EventBridge에서 전달된 frequency 파라미터 (DAILY, WEEKLY, MONTHLY)
  const frequency = event.frequency || "DAILY";

  console.log("[AutoReportHandler] Config:", {
    frequency,
    userPresetTable: USER_PRESET_TABLE,
    backendApiUrl: BACKEND_API_URL,
    region: AWS_REGION,
  });

  try {
    // 1. DynamoDB에서 해당 frequency로 설정된 활성 UserPreset 조회
    const activePresets = await scanActiveUserPresetsByFrequency(frequency);
    console.log(
      `[AutoReportHandler] Found ${activePresets.length} active presets for ${frequency}`
    );

    // 2. 리포트 생성 기간 계산 (한국 시간 기준)
    // Lambda는 UTC에서 실행되므로, 한국 시간(UTC+9)을 명시적으로 계산
    const now = new Date();
    const koreaTime = new Date(now.getTime() + 9 * 60 * 60 * 1000); // UTC + 9시간
    const { since, until } = calculateReportPeriod(frequency, koreaTime);

    console.log(`[AutoReportHandler] Report period: ${since} ~ ${until}`);

    let successCount = 0;
    let failCount = 0;
    const errors = [];

    // 3. 각 사용자별로 리포트 생성
    for (const preset of activePresets) {
      try {
        // PK를 userId로 사용 (DynamoDB 파티션 키)
        const userId = preset.PK?.S || preset.userId?.S;
        const repository = preset.repository?.S;
        const reportStyle = preset.reportStyle?.S || "summary";
        const accessToken = preset.accessToken?.S;

        console.log(
          `[AutoReportHandler] Processing preset: userId=${userId}, repository=${repository}, hasAccessToken=${!!accessToken}`
        );

        // repository 또는 accessToken이 없으면 스킵
        if (!repository) {
          console.log(
            `[AutoReportHandler] Skipping ${userId} - no repository configured`
          );
          continue;
        }

        if (!accessToken) {
          console.log(
            `[AutoReportHandler] Skipping ${userId} - no access token`
          );
          continue;
        }

        console.log(
          `[AutoReportHandler] Generating report for ${userId} (${repository})`
        );

        // 4. 백엔드 API 호출하여 리포트 생성
        const success = await callBackendApiToGenerateReport({
          accessToken,
          repository,
          since,
          until,
          reportStyle,
          userId,
        });

        if (success) {
          successCount++;
          console.log(
            `[AutoReportHandler] Successfully generated report for ${userId}`
          );
        } else {
          failCount++;
          errors.push(`User ${userId}: Report generation failed`);
        }
      } catch (error) {
        failCount++;
        const errorMsg = `User ${preset.userId?.S}: ${error.message}`;
        errors.push(errorMsg);
        console.error(`[AutoReportHandler] ${errorMsg}`, error);
      }
    }

    const result = {
      frequency,
      period: { since, until },
      message: `Automatic report generation completed: ${successCount} success, ${failCount} failed`,
      successCount,
      failCount,
      errors: errors.length > 0 ? errors : undefined,
    };

    console.log("[AutoReportHandler]", result.message);
    return result;
  } catch (error) {
    console.error("[AutoReportHandler] Error:", error);
    throw error;
  }
};

/**
 * DynamoDB에서 특정 frequency로 설정되고 autoReportEnabled=true인 UserPreset 조회
 */
async function scanActiveUserPresetsByFrequency(frequency) {
  const params = {
    TableName: USER_PRESET_TABLE,
    FilterExpression:
      "autoReportEnabled = :enabled AND reportFrequency = :frequency",
    ExpressionAttributeValues: {
      ":enabled": { BOOL: true },
      ":frequency": { S: frequency },
    },
  };

  const command = new ScanCommand(params);
  const response = await dynamoDbClient.send(command);
  return response.Items || [];
}

/**
 * frequency에 따라 리포트 생성 기간 계산
 */
function calculateReportPeriod(frequency, today) {
  switch (frequency.toUpperCase()) {
    case "DAILY":
      return calculateDailyPeriod(today);
    case "WEEKLY":
      return calculateWeeklyPeriod(today);
    case "MONTHLY":
      return calculateMonthlyPeriod(today);
    default:
      throw new Error(`Unknown frequency: ${frequency}`);
  }
}

/**
 * DAILY 리포트 기간: 전날
 */
function calculateDailyPeriod(today) {
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);

  return {
    since: formatDate(yesterday),
    until: formatDate(yesterday),
  };
}

/**
 * WEEKLY 리포트 기간: 지난 주 월요일 ~ 일요일
 */
function calculateWeeklyPeriod(today) {
  // 지난 주 월요일
  const lastMonday = new Date(today);
  lastMonday.setDate(lastMonday.getDate() - 7);

  // 지난 주 일요일
  const lastSunday = new Date(lastMonday);
  lastSunday.setDate(lastSunday.getDate() + 6);

  return {
    since: formatDate(lastMonday),
    until: formatDate(lastSunday),
  };
}

/**
 * MONTHLY 리포트 기간: 지난 달 1일 ~ 마지막 날
 */
function calculateMonthlyPeriod(today) {
  // 지난 달 1일
  const firstDayLastMonth = new Date(
    today.getFullYear(),
    today.getMonth() - 1,
    1
  );
  // 지난 달 마지막 날
  const lastDayLastMonth = new Date(today.getFullYear(), today.getMonth(), 0);

  return {
    since: formatDate(firstDayLastMonth),
    until: formatDate(lastDayLastMonth),
  };
}

/**
 * 날짜를 YYYY-MM-DD 형식으로 포맷
 */
function formatDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * 백엔드 API를 호출하여 리포트 생성
 */
async function callBackendApiToGenerateReport({
  accessToken,
  repository,
  since,
  until,
  reportStyle,
  userId,
}) {
  const apiUrl = `${BACKEND_API_URL}/api/auto-report/generate`;

  const requestBody = {
    accessToken,
    repository,
    since,
    until,
    reportStyle,
    userId,
  };

  console.log(`[AutoReportHandler] Calling backend API: ${apiUrl}`);

  try {
    // 타임아웃 설정 (60초)
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 60000);

    const response = await fetch(apiUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(requestBody),
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    const responseData = await response.json();

    console.log(`[AutoReportHandler] API Response Status: ${response.status}`);
    console.log("[AutoReportHandler] API Response Body:", responseData);

    if (response.ok) {
      return responseData.success === true;
    } else {
      console.error(
        `[AutoReportHandler] Backend API error: ${response.status}`,
        responseData
      );
      return false;
    }
  } catch (error) {
    if (error.name === "AbortError") {
      console.error("[AutoReportHandler] Backend API request timeout (60s)");
    } else {
      console.error("[AutoReportHandler] Failed to call backend API:", error);
    }
    return false;
  }
}
