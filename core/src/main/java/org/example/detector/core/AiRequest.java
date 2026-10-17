package org.example.detector.core;

import java.util.UUID;

/** 标准分析输入。电位 V、电流 μA、浓度 ng/L；selectedDrug 是人工选择，不能当作模型答案。 */
public final class AiRequest {
  public final String requestId, resultId;
  public final DrugType selectedDrug;
  public final DataOrigin dataOrigin;
  public final Signal signal;
  public final double baselinePeak, concentration;
  public final Calibration calibration;

  public AiRequest(DetectionResult result) {
    if (result == null) throw new IllegalArgumentException("缺少待分析记录");
    requestId = UUID.randomUUID().toString();
    resultId = result.id;
    selectedDrug = result.drug;
    dataOrigin = result.origin;
    signal = result.signal;
    baselinePeak = result.baselinePeak;
    concentration = result.concentration;
    calibration = result.calibration;
  }

  public String json() {
    return "{\"schemaVersion\":1,\"requestId\":"
        + AiReport.quote(requestId)
        + ",\"resultId\":"
        + AiReport.quote(resultId)
        + ",\"task\":\"electrochemical-assistance\",\"selectedDrug\":"
        + AiReport.quote(selectedDrug.name())
        + ",\"dataOrigin\":"
        + AiReport.quote(dataOrigin.name())
        + ",\"units\":{\"voltage\":\"V\",\"current\":\"uA\",\"concentration\":\"ng/L\"}"
        + ",\"baselinePeak\":"
        + baselinePeak
        + ",\"concentration\":"
        + concentration
        + ",\"calibration\":{\"id\":"
        + AiReport.quote(calibration.id)
        + ",\"drug\":"
        + AiReport.quote(calibration.drug.name())
        + ",\"slope\":"
        + calibration.slope
        + ",\"intercept\":"
        + calibration.intercept
        + ",\"r\":"
        + calibration.r
        + ",\"min\":"
        + calibration.min
        + ",\"max\":"
        + calibration.max
        + "}"
        + ",\"voltage\":"
        + java.util.Arrays.toString(signal.voltages())
        + ",\"current\":"
        + java.util.Arrays.toString(signal.currents())
        + "}";
  }
}
