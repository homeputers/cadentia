package com.cadentia.reng;

import java.util.List;

public interface TagReportingRepository {

    List<TagUsageReportRow> findRecommendableArrangementTagUsage();
}
