package org.example.detector.core;

import java.util.List;

/** 两端共用的记录读写接口；组合查询按编号包含匹配和类型筛选，结果按时间倒序。 */
public interface ResultRepository {
  void save(DetectionResult result) throws java.io.IOException;

  List<DetectionResult> all() throws java.io.IOException;

  default List<DetectionResult> search(String sampleId, DrugType drug) throws java.io.IOException {
    java.util.ArrayList<DetectionResult> found = new java.util.ArrayList<>();
    for (DetectionResult r : all())
      if ((drug == null || r.drug == drug) && r.sampleId.contains(sampleId)) found.add(r);
    found.sort((a, b) -> Long.compare(b.epochMillis, a.epochMillis));
    return found;
  }
}
