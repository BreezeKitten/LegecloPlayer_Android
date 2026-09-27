package com.legeclo.player;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class LocalWebServer {
    private static final String TAG = "LocalWebServer";
    private final Context context;
    private final int port;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private File storageDir;

    public LocalWebServer(Context context, int port) {
        this.context = context;
        this.port = port;
        this.storageDir = determineDefaultStorage();
    }

    public void setStorageDir(File dir) {
        if (dir != null && dir.exists()) {
            this.storageDir = dir;
            Log.i(TAG, "Storage directory updated to: " + dir.getAbsolutePath());
        }
    }

    public File getStorageDir() {
        return this.storageDir;
    }

    private File determineDefaultStorage() {
        File[] candidates = new File[] {
            new File("/sdcard/Download/LegecloPlayer"),
            new File("/sdcard/LegecloPlayer"),
            context.getExternalFilesDir(null),
            context.getFilesDir()
        };
        for (File f : candidates) {
            if (f != null) {
                if (!f.exists()) f.mkdirs();
                if (f.exists() && f.canRead()) {
                    Log.i(TAG, "Selected storage candidate: " + f.getAbsolutePath());
                    return f;
                }
            }
        }
        return context.getFilesDir();
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
                Log.i(TAG, "Local server started on port " + port);
                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        threadPool.execute(() -> handleClient(socket));
                    } catch (IOException e) {
                        if (!isRunning) break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to bind server socket", e);
            }
        }).start();
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            threadPool.shutdownNow();
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String uri = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                }
            }

            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(out, 200, "OK", "text/plain", new byte[0], null, true);
                return;
            }

            String path = uri;
            Map<String, String> params = new HashMap<>();
            int qIdx = uri.indexOf('?');
            if (qIdx >= 0) {
                path = uri.substring(0, qIdx);
                String qStr = uri.substring(qIdx + 1);
                for (String param : qStr.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        params.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
                    }
                }
            }

            // Route: API
            if (path.equals("/api/characters")) {
                handleApiCharacters(out);
                return;
            } else if (path.equals("/api/chapter")) {
                handleApiChapter(out, params);
                return;
            } else if (path.equals("/api/storage_info")) {
                handleApiStorageInfo(out);
                return;
            }

            // Route: Cache files (MP4, WAV, Spine, etc.)
            if (path.startsWith("/cache/")) {
                handleCacheFile(out, path, headers.get("range"));
                return;
            }

            // Route: Static Web Assets
            handleAssetFile(out, path, headers.get("range"));

        } catch (Exception e) {
            // Socket closed or connection reset by client
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void handleApiCharacters(OutputStream out) throws IOException {
        String json = readAssetString("char_catalog.json");
        if (json == null) json = "[]";

        try {
            // Scan storageDir/cache for existing chapter folders
            File cacheDir = new File(storageDir, "cache");
            Map<String, List<Integer>> cachedMap = new HashMap<>();
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                File[] subDirs = cacheDir.listFiles();
                if (subDirs != null) {
                    for (File d : subDirs) {
                        if (d.isDirectory()) {
                            String name = d.getName();
                            int lastIdx = name.lastIndexOf('_');
                            if (lastIdx > 0 && lastIdx < name.length() - 1) {
                                String cid = name.substring(0, lastIdx);
                                String epStr = name.substring(lastIdx + 1);
                                try {
                                    int ep = Integer.parseInt(epStr);
                                    File cData = new File(d, "chapter_data.json");
                                    if (cData.exists() && cData.length() > 0) {
                                        if (!cachedMap.containsKey(cid)) {
                                            cachedMap.put(cid, new ArrayList<>());
                                        }
                                        cachedMap.get(cid).add(ep);
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }

            JSONArray arr = new JSONArray(json);
            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id");
                if (cachedMap.containsKey(id)) {
                    List<Integer> eps = cachedMap.get(id);
                    JSONArray epsArr = new JSONArray(eps);
                    obj.put("cached_chapters", epsArr);
                    obj.put("is_cached", true);
                } else {
                    obj.put("is_cached", false);
                }
                list.add(obj);
            }

            // Sort: cached characters first!
            Collections.sort(list, (a, b) -> {
                boolean ca = a.optBoolean("is_cached", false);
                boolean cb = b.optBoolean("is_cached", false);
                if (ca && !cb) return -1;
                if (!ca && cb) return 1;
                return 0; // preserve original order
            });

            JSONArray sortedArr = new JSONArray(list);
            byte[] bytes = sortedArr.toString().getBytes(StandardCharsets.UTF_8);
            sendResponse(out, 200, "OK", "application/json; charset=utf-8", bytes, null, true);
            return;
        } catch (Exception e) {
            Log.w(TAG, "Failed to enrich char_catalog with cache info: " + e.getMessage());
        }

        sendResponse(out, 200, "OK", "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), null, true);
    }

    private void handleApiChapter(OutputStream out, Map<String, String> params) throws IOException {
        String cid = params.getOrDefault("char", "tsukuyomi_wedding");
        String ep = params.getOrDefault("ep", "3");
        int epNum = 3;
        try { epNum = Integer.parseInt(ep); } catch (Exception ignored) {}
        String epPadded = String.format(Locale.US, "%02d", epNum);

        // 1. Check local storage for chapter_data.json
        File cDir1 = new File(storageDir, "cache/" + cid + "_" + epPadded);
        File cDir2 = new File(storageDir, "cache/" + cid + "_" + ep);
        File activeDir = cDir1.exists() ? cDir1 : (cDir2.exists() ? cDir2 : null);

        if (activeDir != null) {
            File cData = new File(activeDir, "chapter_data.json");
            if (cData.exists()) {
                String jsonStr = readFileString(cData);
                if (jsonStr != null && !jsonStr.isEmpty()) {
                    sendResponse(out, 200, "OK", "application/json; charset=utf-8", jsonStr.getBytes(StandardCharsets.UTF_8), null, true);
                    return;
                }
            }
        }

        // Fallback default response
        String fallbackJson = "{\"char_id\":\"" + cid + "\",\"character_name\":\"" + cid + "\",\"ep\":\"" + ep + "\",\"title\":\"" + cid + " 第 " + ep + " 話\",\"bg_url\":\"/assets/default_bg.png\",\"bgm_url\":\"/cache/bgm/m_adv_normal01.wav\",\"phases\":[],\"dialogues\":[],\"standing\":{\"has_standing\":false,\"character_id\":\"" + cid + "\"}}";
        sendResponse(out, 200, "OK", "application/json; charset=utf-8", fallbackJson.getBytes(StandardCharsets.UTF_8), null, true);
    }

    private void handleApiStorageInfo(OutputStream out) throws IOException {
        String json = "{\"storage_path\":\"" + storageDir.getAbsolutePath() + "\",\"exists\":" + storageDir.exists() + "}";
        sendResponse(out, 200, "OK", "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), null, true);
    }

    private void handleCacheFile(OutputStream out, String urlPath, String rangeHeader) throws IOException {
        String relPath = urlPath.substring(7); // remove /cache/
        File file = new File(storageDir, "cache/" + relPath);

        // Check if padded version exists (e.g. arthur_wedding_3 -> arthur_wedding_03)
        if (!file.exists()) {
            int slashIdx = relPath.indexOf('/');
            if (slashIdx > 0) {
                String folder = relPath.substring(0, slashIdx);
                String rest = relPath.substring(slashIdx);
                int usIdx = folder.lastIndexOf('_');
                if (usIdx > 0) {
                    String prefix = folder.substring(0, usIdx);
                    String epPart = folder.substring(usIdx + 1);
                    if (epPart.length() == 1 && Character.isDigit(epPart.charAt(0))) {
                        File altFolder = new File(storageDir, "cache/" + prefix + "_0" + epPart + rest);
                        if (altFolder.exists()) file = altFolder;
                    }
                }
            }
        }

        // Fallback for avatar
        if (!file.exists() && relPath.startsWith("avatars/")) {
            File alt = new File(storageDir, "cache/" + relPath.replace("_half.png", ".png"));
            if (alt.exists()) file = alt;
        }

        if (file.exists() && file.isFile()) {
            serveFileStream(out, file, getMimeType(file.getName()), rangeHeader);
            return;
        }

        // Fallback to APK default avatar
        if (urlPath.contains("avatar")) {
            serveAssetStream(out, "assets/default_avatar.png", "image/png", rangeHeader);
            return;
        }

        sendNotFound(out);
    }

    private void handleAssetFile(OutputStream out, String urlPath, String rangeHeader) throws IOException {
        String assetPath = urlPath.equals("/") || urlPath.isEmpty() ? "web/index.html" : "web" + urlPath;
        if (serveAssetStream(out, assetPath, getMimeType(assetPath), rangeHeader)) {
            return;
        }
        // Fallback: try direct path under assets/ (e.g. assets/default_bg.png)
        String altPath = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;
        if (serveAssetStream(out, altPath, getMimeType(altPath), rangeHeader)) {
            return;
        }
        sendNotFound(out);
    }

    private void serveFileStream(OutputStream out, File file, String mime, String rangeHeader) throws IOException {
        long fileSize = file.length();
        long start = 0;
        long end = fileSize - 1;

        boolean isPartial = false;
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            Pattern pattern = Pattern.compile("bytes=(\\d+)-(\\d*)");
            Matcher matcher = pattern.matcher(rangeHeader);
            if (matcher.find()) {
                start = Long.parseLong(matcher.group(1));
                String endStr = matcher.group(2);
                if (endStr != null && !endStr.isEmpty()) {
                    end = Long.parseLong(endStr);
                }
                if (end >= fileSize) end = fileSize - 1;
                isPartial = true;
            }
        }

        long length = end - start + 1;
        PrintWriter headerWriter = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        if (isPartial) {
            headerWriter.print("HTTP/1.1 206 Partial Content\r\n");
            headerWriter.print("Content-Range: bytes " + start + "-" + end + "/" + fileSize + "\r\n");
        } else {
            headerWriter.print("HTTP/1.1 200 OK\r\n");
        }
        headerWriter.print("Content-Type: " + mime + "\r\n");
        headerWriter.print("Content-Length: " + length + "\r\n");
        headerWriter.print("Accept-Ranges: bytes\r\n");
        headerWriter.print("Access-Control-Allow-Origin: *\r\n");
        headerWriter.print("Connection: keep-alive\r\n\r\n");
        headerWriter.flush();

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            byte[] buf = new byte[64 * 1024];
            long bytesRemaining = length;
            while (bytesRemaining > 0) {
                int toRead = (int) Math.min(buf.length, bytesRemaining);
                int read = raf.read(buf, 0, toRead);
                if (read <= 0) break;
                out.write(buf, 0, read);
                bytesRemaining -= read;
            }
            out.flush();
        }
    }

    private boolean serveAssetStream(OutputStream out, String assetPath, String mime, String rangeHeader) {
        try {
            AssetManager am = context.getAssets();
            byte[] data;
            try (InputStream is = am.open(assetPath)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = is.read(b)) != -1) baos.write(b, 0, n);
                data = baos.toByteArray();
            }
            sendResponse(out, 200, "OK", mime, data, rangeHeader, false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void sendResponse(OutputStream out, int status, String msg, String mime, byte[] data, String range, boolean noCache) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        pw.print("HTTP/1.1 " + status + " " + msg + "\r\n");
        pw.print("Content-Type: " + mime + "\r\n");
        pw.print("Content-Length: " + data.length + "\r\n");
        pw.print("Access-Control-Allow-Origin: *\r\n");
        if (noCache) {
            pw.print("Cache-Control: no-cache, no-store, must-revalidate\r\n");
        }
        pw.print("Connection: close\r\n\r\n");
        pw.flush();
        out.write(data);
        out.flush();
    }

    private void sendNotFound(OutputStream out) throws IOException {
        byte[] b = "404 Not Found".getBytes(StandardCharsets.UTF_8);
        sendResponse(out, 404, "Not Found", "text/plain", b, null, false);
    }

    private String readAssetString(String name) {
        try (InputStream is = context.getAssets().open(name)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString("UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Failed to read asset: " + name, e);
            return null;
        }
    }

    private String readFileString(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private String getMimeType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".atlas")) return "text/plain; charset=utf-8";
        if (n.endsWith(".skel")) return "application/octet-stream";
        return "application/octet-stream";
    }
}
