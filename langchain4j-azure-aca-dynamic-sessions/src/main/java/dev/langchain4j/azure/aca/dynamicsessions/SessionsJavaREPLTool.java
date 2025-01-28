package dev.langchain4j.azure.aca.dynamicsessions;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * A tool for executing Java code in Azure ACA dynamic sessions.
 * Usage:
 *
 * SessionsJavaREPLTool replTool = new SessionsJavaREPLTool("https://your-endpoint.com/");
 * String result = replTool.use("System.out.println(\"Hello, World!\");");
 *
 */
public class SessionsJavaREPLTool {

    private static final String USER_AGENT = "langchain4j-azure-dynamic-sessions-java/1.0.0-alpha2-SNAPSHOT (Language=Java)";
    private static final String API_VERSION = "2024-09-09-preview";
    private static final Pattern SANITIZE_PATTERN_START = Pattern.compile("^(\\s|`)*(?i:java)?\\s*");
    private static final Pattern SANITIZE_PATTERN_END = Pattern.compile("(\\s|`)*$");

    private final String name = "java_REPL";
    private final String description =
            "Use this to execute java commands when you need to perform calculations or computations. Input should be a valid java command. Returns a JSON object with the result, stdout, and stderr.";
    private final boolean sanitizeInput;
    private final String poolManagementEndpoint;
    private final String sessionId;
    private final HttpClient httpClient;
    private final DefaultAzureCredential credential;
    private final AtomicReference<AccessToken> accessTokenRef = new AtomicReference<>();

    /**
     * Constructs a new SessionsJavaREPLTool with the specified endpoint.
     *
     * @param poolManagementEndpoint the pool management endpoint URL
     */
    public SessionsJavaREPLTool(String poolManagementEndpoint) {
        this(poolManagementEndpoint, UUID.randomUUID().toString(), true);
    }

    /**
     * Constructs a new SessionsJavaREPLTool with specified parameters.
     *
     * @param poolManagementEndpoint the pool management endpoint URL
     * @param sessionId the session ID
     * @param sanitizeInput whether to sanitize the input code
     */
    public SessionsJavaREPLTool(String poolManagementEndpoint, String sessionId, boolean sanitizeInput) {
        this.poolManagementEndpoint = poolManagementEndpoint;
        this.sessionId = sessionId;
        this.sanitizeInput = sanitizeInput;
        this.httpClient = HttpClient.newBuilder().build();
        this.credential = new DefaultAzureCredentialBuilder().build();
    }

    /**
     * Executes the input code and returns the result.
     *
     * @param input the Java code to execute
     * @return the execution result as a JSON string
     */
    @Tool(name = "java_REPL")
    public String use(String input) {
        Map<String, Object> response = execute(input);

        Object result = response.get("result");
        if (result instanceof Map<?, ?>) {
            Map<?, ?> resultMap = (Map<?, ?>) result;
            if ("image".equals(resultMap.get("type")) && resultMap.containsKey("base64_data")) {
                resultMap.remove("base64_data");
            }
        }

        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("result", result);
        contentMap.put("stdout", response.get("stdout"));
        contentMap.put("stderr", response.get("stderr"));

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(contentMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response.", e);
        }
    }

    /**
     * Returns the value of the tool.
     *
     * @return an array containing the tool name
     */
    public String[] value() {
        return new String[] {"Java REPL Tool"};
    }

    private String getAccessToken() {
        AccessToken token = accessTokenRef.get();
        if (token == null || token.isExpired()) {
            try {
                TokenRequestContext context =
                        new TokenRequestContext().addScopes("https://dynamicsessions.io/.default");
                token = credential.getToken(context).block();
                if (token != null) {
                    accessTokenRef.set(token);
                } else {
                    throw new RuntimeException("Failed to acquire access token.");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to acquire access token.", e);
            }
        }
        return token.getToken();
    }

    private String buildUrl(String path) {
        String endpoint = poolManagementEndpoint;
        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        String encodedSessionId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
        String query = "identifier=" + encodedSessionId + "&api-version=" + API_VERSION;
        return endpoint + path + "?" + query;
    }

    private String sanitizeInput(String input) {
        input = SANITIZE_PATTERN_START.matcher(input).replaceAll("");
        input = SANITIZE_PATTERN_END.matcher(input).replaceAll("");
        return input;
    }

    /**
     * Executes the given Java code.
     *
     * @param javaCode the Java code to execute
     * @return a map containing the execution results
     */
    public Map<String, Object> execute(String javaCode) {
        if (sanitizeInput) {
            javaCode = sanitizeInput(javaCode);
        }

        String accessToken = getAccessToken();
        String apiUrl = buildUrl("code/execute");

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("codeInputType", "inline");
        properties.put("executionType", "synchronous");
        properties.put("code", javaCode);
        body.put("properties", properties);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> responseJson = objectMapper.readValue(response.body(), Map.class);
                return (Map<String, Object>) responseJson.get("properties");
            } else {
                throw new RuntimeException(
                        "Request failed with status code " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute code.", e);
        }
    }

    /**
     * Uploads a file to the remote session.
     *
     * @param data the file data as an InputStream
     * @param remoteFilePath the path where the file will be stored remotely
     * @return metadata about the uploaded file
     */
    public RemoteFileMetadata uploadFile(InputStream data, String remoteFilePath) {
        String accessToken = getAccessToken();
        String apiUrl = buildUrl("files/upload");

        try {
            String boundary = UUID.randomUUID().toString();
            HttpRequest.BodyPublisher bodyPublisher = buildMultipartFormData(data, remoteFilePath, boundary);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", USER_AGENT)
                    .POST(bodyPublisher)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectMapper objectMapper = new ObjectMapper();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> responseJson = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> valueList = (List<Map<String, Object>>) responseJson.get("value");
                Map<String, Object> fileMetadataMap = valueList.get(0);
                return RemoteFileMetadata.fromDict(fileMetadataMap);
            } else {
                throw new RuntimeException(
                        "File upload failed with status code " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file.", e);
        }
    }

    private HttpRequest.BodyPublisher buildMultipartFormData(InputStream data, String remoteFilePath, String boundary)
            throws IOException {
        String LINE_FEED = "\r\n";
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8);
        BufferedWriter writer = new BufferedWriter(outputStreamWriter);

        // Write file part header
        writer.write("--" + boundary);
        writer.write(LINE_FEED);
        writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"" + remoteFilePath + "\"");
        writer.write(LINE_FEED);
        writer.write("Content-Type: application/octet-stream");
        writer.write(LINE_FEED);
        writer.write(LINE_FEED);
        writer.flush();

        // Write file content
        data.transferTo(byteArrayOutputStream);

        // End of multipart/form-data.
        writer.write(LINE_FEED);
        writer.write("--" + boundary + "--");
        writer.write(LINE_FEED);
        writer.flush();

        return HttpRequest.BodyPublishers.ofByteArray(byteArrayOutputStream.toByteArray());
    }

    /**
     * Downloads a file from the remote session.
     *
     * @param remoteFilePath the path of the remote file
     * @return an InputStream of the file content
     */
    public InputStream downloadFile(String remoteFilePath) {
        String accessToken = getAccessToken();
        String encodedRemoteFilePath = URLEncoder.encode(remoteFilePath, StandardCharsets.UTF_8);
        String apiUrl = buildUrl("files/content/" + encodedRemoteFilePath);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                throw new RuntimeException("File download failed with status code " + response.statusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file.", e);
        }
    }

    /**
     * Lists all files in the remote session.
     *
     * @return a list of remote file metadata
     */
    public List<RemoteFileMetadata> listFiles() {
        String accessToken = getAccessToken();
        String apiUrl = buildUrl("files");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectMapper objectMapper = new ObjectMapper();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> responseJson = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> valueList = (List<Map<String, Object>>) responseJson.get("value");
                List<RemoteFileMetadata> fileMetadataList = new ArrayList<>();
                for (Map<String, Object> fileMetadataMap : valueList) {
                    fileMetadataList.add(RemoteFileMetadata.fromDict(fileMetadataMap));
                }
                return fileMetadataList;
            } else {
                throw new RuntimeException("Failed to list files with status code " + response.statusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list files.", e);
        }
    }

    /**
     * Represents metadata of a remote file.
     */
    public static class RemoteFileMetadata {
        private final String filename;
        private final long sizeInBytes;

        /**
         * Constructs a RemoteFileMetadata instance.
         *
         * @param filename the name of the file
         * @param sizeInBytes the size of the file in bytes
         */
        public RemoteFileMetadata(String filename, long sizeInBytes) {
            this.filename = filename;
            this.sizeInBytes = sizeInBytes;
        }

        /**
         * Gets the filename.
         *
         * @return the filename
         */
        public String getFilename() {
            return filename;
        }

        /**
         * Gets the size of the file in bytes.
         *
         * @return the size in bytes
         */
        public long getSizeInBytes() {
            return sizeInBytes;
        }

        /**
         * Gets the full path of the file.
         *
         * @return the full file path
         */
        public String getFullPath() {
            return "/mnt/data/" + filename;
        }

        /**
         * Creates a RemoteFileMetadata instance from a map.
         *
         * @param data the map containing file properties
         * @return a RemoteFileMetadata instance
         */
        public static RemoteFileMetadata fromDict(Map<String, Object> data) {
            Map<String, Object> properties = (Map<String, Object>) data.get("properties");
            String filename = (String) properties.get("filename");
            Number size = (Number) properties.get("size");
            return new RemoteFileMetadata(filename, size.longValue());
        }
    }
}
