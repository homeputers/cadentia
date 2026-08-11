package com.cadentia.search;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import java.util.List;

public interface ApprovedSearchDocumentProvider {

    List<ApprovedSearchDocument> documents();
}
