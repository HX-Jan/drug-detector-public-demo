package org.example.detector.core;

import java.io.IOException;

/** AI 服务适配接口；真实服务可独立实现此接口，无需公开模型源码或权重。 */
public interface AiAnalyzer {
  AiReport analyze(AiRequest request) throws IOException;
}
