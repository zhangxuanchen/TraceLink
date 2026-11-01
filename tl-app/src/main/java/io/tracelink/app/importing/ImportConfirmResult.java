package io.tracelink.app.importing;

import java.util.List;

public record ImportConfirmResult(Long batchId, String status, int created, int merged, int skipped, int failed,
                                  List<Long> taskIds, String message) {
}
