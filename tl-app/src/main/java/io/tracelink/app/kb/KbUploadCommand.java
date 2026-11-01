package io.tracelink.app.kb;

/** 上传入参：应用层不直接接触 MultipartFile */
public record KbUploadCommand(
        String title,
        String domain,
        Long deptId,
        String visibility,
        String fileName,
        String sourceType) {
}
