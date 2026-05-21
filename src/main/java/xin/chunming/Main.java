package xin.chunming;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.TreeMap;

public class Main {
    static ArrayList<File> all = new ArrayList<>();
    static TreeMap<Long, File> audioMap = new TreeMap<>();
    static TreeMap<Long, File> videoMap = new TreeMap<>();
    final static String VideoTrackCode = "746668640000000000000001";
    final static String VideoTrackCode_2 = "7466686400000001";
    final static String AudioTrackCode = "746668640000000000000002";
    final static String AudioTrackCode_2 = "7466686400000002";

    public static void main(String[] args) throws IOException {
        File file = new File("/Users/fuchunming/Downloads/未命名文件夹 2");
        if (!file.isDirectory()) {
            System.out.println("目录不存在！");
            return;
        }

        File[] files = file.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.getName().endsWith(".m4s")) {
                all.add(f);
            }
        }

        for (File file1 : all) {
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file1))) {
                // 读取前 1024 字节用于解析头部
                byte[] bytes = new byte[1024];
                int readLen = bis.read(bytes);
                if (readLen < 8) continue;

                ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, readLen);

                // 1. 检查是否是 ftyp 头文件（初始化段）
                int firstSlice = buffer.getInt(0); // 前4字节是大小
                int type = buffer.getInt(4);       // 后4字节是类型

                if (type == 0x66747970) { // "ftyp"
                    // 将前 1024 字节转为十六进制仅用于判断含有的字符串
                    String hexStr = bytesToHex(bytes, readLen);
                    if (hexStr.contains("736f756e")) { // "soun" -> 音频初始化段
                        System.out.println("检测到音频 ftyp: " + file1.getName());
                        audioMap.put(0L, file1); // 初始化段的时间戳设为 0，排在最前面
                    } else if (hexStr.contains("76696465")) { // "vide" -> 视频初始化段
                        System.out.println("检测到视频 ftyp: " + file1.getName());
                        videoMap.put(0L, file1);
                    }
                    continue;
                }

                // 2. 如果是普通媒体切片，动态查找 moof -> traf -> tfhd 和 tfdt
                String hexStr = bytesToHex(bytes, readLen);

                // 寻找 tfhd 区分音视频 (74666864)
                int tfhdPos = hexStr.indexOf("74666864");
                if (tfhdPos != -1) {
                    // tfhd 后面紧跟的通常是 version(1字节) + flags(3字节) + track_id(4字节)
                    // 为了绝对安全，我们直接用你原本的特征码判断 Track ID
                    boolean isVideo = hexStr.contains(VideoTrackCode) || hexStr.contains(VideoTrackCode_2);
                    boolean isAudio = hexStr.contains(AudioTrackCode) || hexStr.contains(AudioTrackCode_2);

                    // 动态寻找 tfdt 盒 (74666474) 获取精确的 baseMediaDecodeTime
                    int tfdtPos = hexStr.indexOf("74666474");/*tfdt*/
                    if (tfdtPos != -1) {
                        // tfdtPos 是十六进制字符串索引，除以 2 得到字节索引
                        int tfdtByteIdx = tfdtPos / 2;

                        // tfdt 结构：4字节size + 4字节"tfdt" + 1字节version + 3字节flags
                        int version = bytes[tfdtByteIdx + 8] & 0xFF;

                        long pts = 0;
                        if (version == 1) {
                            // version 1: 64位时间戳 (8字节)
                            pts = buffer.getLong(tfdtByteIdx + 12);
                        } else {
                            // version 0: 32位时间戳 (4字节)
                            pts = buffer.getInt(tfdtByteIdx + 12) & 0xFFFFFFFFL;
                        }

                        if (isVideo) {
                            System.out.println("视频切片 -> PTS: " + pts + " 文件: " + file1.getName());
                            videoMap.put(pts, file1);
                        } else if (isAudio) {
                            System.out.println("音频切片 -> PTS: " + pts + " 文件: " + file1.getName());
                            audioMap.put(pts, file1);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("解析文件失败: " + file1.getName() + " 原因: " + e.getMessage());
            }
        }

        // 写入合并后的文件
        try (BufferedOutputStream bosAudio = new BufferedOutputStream(new FileOutputStream("output_audio.mp4"))) {
            writeMapToStream(audioMap, bosAudio);
        }
        System.out.println("ok");
        try (BufferedOutputStream bosVideo = new BufferedOutputStream(new FileOutputStream("output_video.mp4"))) {
            writeMapToStream(videoMap, bosVideo);
        }

        System.out.println("合并完成！生成了 output_audio.mp4 和 output_video.mp4");
    }

    private static void writeMapToStream(TreeMap<Long, File> map, BufferedOutputStream bos) throws IOException {
        for (File f : map.values()) {
            System.out.println(f.getName());
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
            }
        }
        bos.flush();
    }

    // 辅助方法：安全地将 byte 数组转为指定长度的十六进制字符串
    private static String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
