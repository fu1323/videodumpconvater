const rawAppend = SourceBuffer.prototype.appendBuffer;

// 计数器，方便你后端按顺序或者根据日志排查
window.__chunkCounter = 0;

SourceBuffer.prototype.appendBuffer = function(buf) {
    window.__chunkCounter++;
    const chunkId = window.__chunkCounter;

    // 确保拿到正确的 ArrayBuffer
    const bufferToSave = buf.buffer ? buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength) : buf;
    console.log(`[Hook] 捕获到分片 #${chunkId}, 大小: ${bufferToSave.byteLength} 字节`);

    // 1. 【核心修正】用方括号把 buffer 包裹成数组
    const blob = new Blob([bufferToSave], { type: "video/mp4" });
    const url = URL.createObjectURL(blob);

    // 2. 触发下载
    const a = document.createElement("a");
    a.href = url;
    // 自动带上序号，方便你后端区分音频流和视频流（通常可以通过大小或者内容特征区分）
    a.download = `chunk1_${chunkId}.m4s`;
    a.click();

    // 3. 延迟释放 URL 内存，防止内存泄漏
    setTimeout(() => {
        URL.revokeObjectURL(url);
    }, 5000);

    // 执行原生逻辑
    return rawAppend.apply(this, arguments);
};
