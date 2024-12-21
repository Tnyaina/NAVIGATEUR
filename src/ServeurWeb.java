import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ServeurWeb {
    // Variables de configuration
    private static int PORT;
    private static String XAMPP_SERVER_URL;
    private static long DYNAMIC_PAGE_EXPIRATION;
    private static long STATIC_PAGE_EXPIRATION;
    private static int CACHE_CLEANUP_INTERVAL;
    private static List<String> DYNAMIC_PAGE_KEYWORDS;

    // Cache et sessions
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, CacheEntry>> USER_CACHES = new ConcurrentHashMap<>();    private static final HashMap<String, Map<String, String>> PHP_SESSIONS = new HashMap<>();
    private static final ScheduledExecutorService cacheCleanupService = Executors.newSingleThreadScheduledExecutor();

    // Classe de cache améliorée
    static class CacheEntry {
        private final String content; // contenue de la page
        private final long creationTime; // date de creation
        private final boolean isDynamic; // type de page
        private final String method; // methode http get , post
        private final Map<String, String> parameters; // parametre de requete

        public CacheEntry(String content, boolean isDynamic, String method, Map<String, String> parameters) {
            this.content = content;
            this.creationTime = System.currentTimeMillis();
            this.isDynamic = isDynamic;
            this.method = method;
            this.parameters = new HashMap<>(parameters);
        }

        public boolean isExpired() {
            long expirationTime = isDynamic ? DYNAMIC_PAGE_EXPIRATION : STATIC_PAGE_EXPIRATION;
            return System.currentTimeMillis() - creationTime > expirationTime;
        }

        public boolean matchesRequest(String method, Map<String, String> currentParams) {
            if (isDynamic) {
                return this.method.equals(method) && this.parameters.equals(currentParams);
            }
            return true;
        }

        public String getContent() {
            return content;
        }
    }

    // Charger la configuration depuis le fichier JSON
    private static void loadConfiguration() {
        try {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(new FileReader("C:/Users/Nyx/IdeaProjects/NAVIGATEUR/src/conf.json"));
            JSONObject jsonObject = (JSONObject) obj;

            JSONObject serverConfig = (JSONObject) jsonObject.get("server");
            PORT = ((Long) serverConfig.get("port")).intValue();
            XAMPP_SERVER_URL = (String) serverConfig.get("xampp_server_url");

            JSONObject cacheConfig = (JSONObject) jsonObject.get("cache");
            DYNAMIC_PAGE_EXPIRATION = (Long) cacheConfig.get("dynamic_page_expiration_ms");
            STATIC_PAGE_EXPIRATION = (Long) cacheConfig.get("static_page_expiration_ms");
            CACHE_CLEANUP_INTERVAL = ((Long) cacheConfig.get("cleanup_interval_minutes")).intValue();

            JSONObject dynamicPageConfig = (JSONObject) jsonObject.get("dynamic_page_detection");
            DYNAMIC_PAGE_KEYWORDS = (List<String>) dynamicPageConfig.get("keywords");

        } catch (IOException | ParseException e) {
            // Configuration par défaut
            PORT = 1567;
            XAMPP_SERVER_URL = "http://localhost:80";
            DYNAMIC_PAGE_EXPIRATION = 60_000;
            STATIC_PAGE_EXPIRATION = 5 * 60 * 60_000;
            CACHE_CLEANUP_INTERVAL = 5;
            DYNAMIC_PAGE_KEYWORDS = Arrays.asList(
                    "submit", "process", "handle", "result", "action", "traitement"
            );
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        loadConfiguration();

        // Démarrer le service de nettoyage du cache
        cacheCleanupService.scheduleAtFixedRate(() -> {
            USER_CACHES.forEach((userId, userCache) -> {
                userCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            });
            USER_CACHES.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }, 1, CACHE_CLEANUP_INTERVAL, TimeUnit.MINUTES);

        // Démarrer le gestionnaire de commandes dans un thread séparé
        Thread commandThread = new Thread(new CacheCommandHandler());
        commandThread.setDaemon(true);
        commandThread.start();

        System.out.println("Serveur démarré sur le port " + PORT);
        serverSocket = new ServerSocket(PORT);

        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connexion entrante : " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            } catch (SocketException e) {
                if (!isRunning) {
                    System.out.println("Serveur arrêté.");
                    break;
                }
                e.printStackTrace();
            }
        }
    }

    static class ClientHandler implements Runnable { // traitement des requetes
        private Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private String method;
        private String path;
        private String sessionId;
        private String userId;
        private Map<String, String> requestHeaders = new HashMap<>();
        private Map<String, String> requestParams = new HashMap<>();

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Lire l'ensemble de la requête HTTP
                String requestLine = in.readLine(); // exemple : GET /index.php HTTP/1.1
                if (requestLine == null || requestLine.isEmpty()) {
                    return;
                }

                String[] requestParts = requestLine.split(" ");
                method = requestParts[0];  // GET ou POST
                path = requestParts[1];    // Le chemin demandé : /index.php
                System.out.println("Requête reçue : Méthode " + method + " pour le chemin " + path);

                // Lire les en-têtes
                String headerLine;
                while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                    String[] headerParts = headerLine.split(": ", 2);
                    if (headerParts.length == 2) {
                        requestHeaders.put(headerParts[0].toLowerCase(), headerParts[1]);
                    }
                }

                // Générer l'ID utilisateur basé sur User-Agent et IP
                userId = requestHeaders.getOrDefault("user-agent", "") + "-" +
                        clientSocket.getInetAddress().toString() + "-" +
                        clientSocket.getPort();

                USER_CACHES.putIfAbsent(userId, new ConcurrentHashMap<>());

                // Gestion des paramètres GET
                // path ohatra : /search.php?query=test&page=1
                if (path.contains("?")) {
                    String[] pathAndQuery = path.split("\\?"); // parsena ny path
                    path = pathAndQuery[0]; // /search.php
                    parseQueryString(pathAndQuery[1]); // fonction manao ny parsage (atao anaty map) // "query=test&page=1"
                }
                // requestParams contiendra:
                // {
                //     "query": "test",
                //     "page": "1"
                // }

                // Gestion des paramètres POST
                if ("POST".equalsIgnoreCase(method)) {
                    int contentLength = Integer.parseInt(requestHeaders.getOrDefault("content-length", "0"));
                    if (contentLength > 0) {
                        char[] buffer = new char[contentLength];
                        in.read(buffer, 0, contentLength);
                        String postData = new String(buffer);
                        parseQueryString(postData);
                    }
                }

                // Gestion des sessions PHP
                sessionId = getOrCreateSession();

                // Vérification du cache avant d'aller sur le serveur
                String cacheKey = generateCacheKey(path, requestParams);
                ConcurrentHashMap<String, CacheEntry> userCache = USER_CACHES.get(userId);
                CacheEntry cachedEntry = userCache.get(cacheKey);

                if (cachedEntry != null &&  // Si une entrée existe dans le cache
                        !cachedEntry.isExpired() &&  // Si elle n'est pas expirée
                        cachedEntry.matchesRequest(method, requestParams)) {  // Si elle correspond à la requête actuelle
                    // Utiliser le contenu du cache
                    System.out.println("Utilisation du cache pour : " + path);
                    sendResponse("200 OK", "text/html; charset=UTF-8", cachedEntry.getContent());
                    return;  // On arrête là si on a trouvé dans le cache
                }

                if (path.equals("/cache/list") || path.equals("/cache/remove")) {
                    handleCacheManagement();
                    return;
                }

                // Si rien n'a été trouvé dans le cache ou si c'est expiré,
                // on transmet la requête à XAMPP
                forwardRequestToXampp(path, sessionId);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Générer une clé de cache unique

        // exemple de cle /search.php?page=1&query=test ( tode le requete mhts)
        private String generateCacheKey(String path, Map<String, String> params) {
            if (params.isEmpty()) {
                return path;
            }

            // Trier les paramètres pour une clé cohérente
            String paramString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));

            return path + "?" + paramString;
        }

        private void parseQueryString(String queryString) {
            String[] params = queryString.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    try {
                        String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.toString());
                        String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.toString());
                        requestParams.put(key, value);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private String getOrCreateSession() {
            // Vérifier si un cookie de session existe
            String existingSessionId = requestHeaders.get("cookie");
            if (existingSessionId != null && PHP_SESSIONS.containsKey(existingSessionId)) {
                System.out.println("Session existante trouvée : " + existingSessionId);
                return existingSessionId;
            }

            // Créer une nouvelle session
            String newSessionId = "PHPSESSID=" + UUID.randomUUID().toString();
            PHP_SESSIONS.put(newSessionId, new HashMap<>());
            System.out.println("Nouvelle session créée : " + newSessionId);
            return newSessionId;
        }

        private void forwardRequestToXampp(String path, String sessionId) throws IOException {
            URL url = new URL(XAMPP_SERVER_URL + path); // ny lien // exemple : GET /index.php HTTP/1.1
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Configurer la méthode HTTP
            connection.setRequestMethod(method); // zay methode natao
            connection.setDoOutput(true);

            // Transmettre les en-têtes
            connection.setRequestProperty("Cookie", sessionId);

            // Transmettre les paramètres POST si nécessaire
            if ("POST".equalsIgnoreCase(method) && !requestParams.isEmpty()) {
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    StringBuilder postData = new StringBuilder();
                    for (Map.Entry<String, String> param : requestParams.entrySet()) {
                        if (postData.length() != 0) postData.append('&');
                        postData.append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8));
                        postData.append('=');
                        postData.append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8));
                    }
                    wr.writeBytes(postData.toString()); // eto no ny parametre requete deja parser username=john&password=secretpass123
                }
            }

            // Lire et transmettre la réponse
            int responseCode = connection.getResponseCode();
            // rah mbola tsy en cache
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder responseContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line).append("\n");
                }
                reader.close();

                // Déterminer si la page est dynamique
                boolean isDynamic = isDynamicPage(path, method, requestParams);

                // Ajouter au cache dynamique

                // creena ny objet cache i stockena azy
                String cacheKey = generateCacheKey(path, requestParams);
                CacheEntry entry = new CacheEntry(
                        responseContent.toString(),
                        isDynamic,
                        method,
                        requestParams
                );

                // ampidirina am Map misy ny cache rehetra
                USER_CACHES.get(userId).put(cacheKey, entry);

                // Gérer la réponse
                sendResponse("200 OK", connection.getContentType(), responseContent.toString());
            } else {
                sendError("404 Not Found", "Ressource PHP introuvable");
            }
        }

        private boolean isDynamicPage(String path, String method, Map<String, String> requestParams) {
            return method.equals("POST") ||
                    requestParams.size() > 0 ||
                    path.contains("?") ||
                    DYNAMIC_PAGE_KEYWORDS.stream().anyMatch(path::contains);
        }

        private void sendResponse(String status, String contentType, String content) {
            out.println("HTTP/1.1 " + status);
            out.println("Content-Type: " + contentType);
            out.println();
            out.println(content);
            System.out.println("Réponse envoyée : " + status);
        }

        private void sendError(String status, String errorMessage) {
            out.println("HTTP/1.1 " + status);
            out.println("Content-Type: text/html; charset=UTF-8");
            out.println();
            out.println("<html><body><h1>" + status + "</h1><p>" + errorMessage + "</p></body></html>");
            System.out.println("Erreur envoyée : " + status);
        }

        private void handleCacheManagement() throws IOException {
            if (path.equals("/cache/list")) {
                StringBuilder htmlResponse = new StringBuilder();
                htmlResponse.append("<!DOCTYPE html>\n")
                        .append("<html><head><title>Gestionnaire de Cache</title>")
                        .append("<style>")
                        .append("table { width: 100%; border-collapse: collapse; margin-top: 20px; }")
                        .append("th, td { padding: 10px; border: 1px solid #ddd; text-align: left; }")
                        .append("th { background-color: #f5f5f5; }")
                        .append(".remove-btn { background-color: #ff4444; color: white; border: none; ")
                        .append("padding: 5px 10px; cursor: pointer; }")
                        .append("</style></head><body>")
                        .append("<h1>Gestionnaire de Cache</h1>")
                        .append("<table>")
                        .append("<tr><th>URL</th><th>Expiration</th><th>Type</th><th>User ID</th><th>Action</th></tr>");

                List<CacheInfo> cacheInfos = ServeurWeb.listCacheEntries();

                for (CacheInfo info : cacheInfos) {
                    long timeRemaining = (info.getExpirationTime() - System.currentTimeMillis()) / 1000;
                    htmlResponse.append("<tr>")
                            .append("<td>").append(info.getUrl()).append("</td>")
                            .append("<td>").append(timeRemaining).append(" seconds</td>")
                            .append("<td>").append(info.isDynamic() ? "Dynamic" : "Static").append("</td>")
                            .append("<td>").append(info.getUserId()).append("</td>")
                            .append("<td><form method='POST' action='/cache/remove'>")
                            .append("<input type='hidden' name='url' value='").append(info.getUrl()).append("'>")
                            .append("<input type='hidden' name='userId' value='").append(info.getUserId()).append("'>")
                            .append("<input type='submit' value='Supprimer' class='remove-btn'>")
                            .append("</form></td>")
                            .append("</tr>");
                }

                htmlResponse.append("</table></body></html>");

                sendResponse("200 OK", "text/html; charset=UTF-8", htmlResponse.toString());
            }
            else if (path.equals("/cache/remove") && method.equals("POST")) {
                String urlToRemove = requestParams.get("url");
                String userIdToRemove = requestParams.get("userId");

                System.out.println("Tentative de suppression - URL: " + urlToRemove + ", UserID: " + userIdToRemove); // Debug

                if (urlToRemove != null && userIdToRemove != null) {
                    ServeurWeb.removeFromCache(urlToRemove, userIdToRemove);
                    // Redirection avec le protocole HTTP complet
                    out.println("HTTP/1.1 302 Found");
                    out.println("Location: /cache/list");
                    out.println(); // Ligne vide importante pour la réponse HTTP
                    out.flush(); // Assurer que la réponse est envoyée
                } else {
                    sendError("400 Bad Request", "URL ou UserID manquant");
                }
            }
        }
    }

    // fonctionnaliter de listing
    public static class CacheInfo {
        private final String url;
        private final long expirationTime;
        private final boolean isDynamic;
        private final String userId;

        public CacheInfo(String url, long expirationTime, boolean isDynamic, String userId) {
            this.url = url;
            this.expirationTime = expirationTime;
            this.isDynamic = isDynamic;
            this.userId = userId;
        }

        public String getUrl() { return url; }
        public long getExpirationTime() { return expirationTime; }
        public boolean isDynamic() { return isDynamic; }
        public String getUserId() { return userId; }
    }

    public static List<CacheInfo> listCacheEntries() {
        List<CacheInfo> cacheInfos = new ArrayList<>();

        USER_CACHES.forEach((userId, userCache) -> {
            userCache.forEach((url, entry) -> {
                long expirationTime = entry.isDynamic ?
                        entry.creationTime + DYNAMIC_PAGE_EXPIRATION :
                        entry.creationTime + STATIC_PAGE_EXPIRATION;

                cacheInfos.add(new CacheInfo(
                        url,
                        expirationTime,
                        entry.isDynamic,
                        userId
                ));
            });
        });

        return cacheInfos;
    }
    public static void removeFromCache(String url, String userId) {
        ConcurrentHashMap<String, CacheEntry> userCache = USER_CACHES.get(userId);
        if (userCache != null) {
            userCache.remove(url);
            if (userCache.isEmpty()) {
                USER_CACHES.remove(userId);
            }
        }
    }

    // Nouvelle classe pour gérer les commandes
    static class CacheCommandHandler implements Runnable {
        private final BufferedReader consoleReader;
        private volatile boolean running = true;

        public CacheCommandHandler() {
            this.consoleReader = new BufferedReader(new InputStreamReader(System.in));
        }

        @Override
        public void run() {
            System.out.println("\n=== Commandes disponibles ===");
            System.out.println("set-dynamic-expiration <milliseconds> : Modifier l'expiration des pages dynamiques");
            System.out.println("set-static-expiration <milliseconds> : Modifier l'expiration des pages statiques");
            System.out.println("clear-all : Supprimer tous les caches");
            System.out.println("stats : Afficher les statistiques du cache");
            System.out.println("help : Afficher l'aide");
            System.out.println("exit : Quitter le serveur");
            System.out.println("================================\n");

            while (running) {
                try {
                    System.out.print("> ");
                    String command = consoleReader.readLine();
                    if (command == null || command.trim().isEmpty()) continue;

                    processCommand(command.trim());
                } catch (IOException e) {
                    System.err.println("Erreur de lecture de la commande: " + e.getMessage());
                }
            }
        }

        private void processCommand(String command) {
            String[] parts = command.split("\\s+");
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "set-dynamic-expiration":
                        if (parts.length < 2) {
                            System.out.println("Usage: set-dynamic-expiration <milliseconds>");
                            return;
                        }
                        long dynamicExpiration = Long.parseLong(parts[1]);
                        ServeurWeb.setDynamicPageExpiration(dynamicExpiration);
                        System.out.println("Expiration des pages dynamiques modifiée à " + dynamicExpiration + "ms");
                        break;

                    case "set-static-expiration":
                        if (parts.length < 2) {
                            System.out.println("Usage: set-static-expiration <milliseconds>");
                            return;
                        }
                        long staticExpiration = Long.parseLong(parts[1]);
                        ServeurWeb.setStaticPageExpiration(staticExpiration);
                        System.out.println("Expiration des pages statiques modifiée à " + staticExpiration + "ms");
                        break;

                    case "clear-all":
                        ServeurWeb.clearAllCaches();
                        System.out.println("Tous les caches ont été supprimés");
                        break;

                    case "stats":
                        printCacheStats();
                        break;

                    case "help":
                        printHelp();
                        break;

                    case "exit":
                        System.out.println("Arrêt du serveur...");
                        ServeurWeb.stopServer();
                        running = false;
                        break;

                    default:
                        System.out.println("Commande inconnue. Tapez 'help' pour voir les commandes disponibles.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Erreur: La valeur doit être un nombre valide en millisecondes");
            }
        }

        private void printCacheStats() {
            List<ServeurWeb.CacheInfo> cacheInfos = ServeurWeb.listCacheEntries();
            int totalEntries = cacheInfos.size();
            long dynamicEntries = cacheInfos.stream().filter(ServeurWeb.CacheInfo::isDynamic).count();
            long staticEntries = totalEntries - dynamicEntries;

            System.out.println("\n=== Statistiques du Cache ===");
            System.out.println("Nombre total d'entrées: " + totalEntries);
            System.out.println("Pages dynamiques: " + dynamicEntries);
            System.out.println("Pages statiques: " + staticEntries);
            System.out.println("Expiration dynamique: " + ServeurWeb.getDynamicPageExpiration() + "ms");
            System.out.println("Expiration statique: " + ServeurWeb.getStaticPageExpiration() + "ms");
            System.out.println("==========================\n");
        }

        private void printHelp() {
            System.out.println("\n=== Aide des commandes ===");
            System.out.println("set-dynamic-expiration <milliseconds> : Définir le temps d'expiration des pages dynamiques");
            System.out.println("set-static-expiration <milliseconds> : Définir le temps d'expiration des pages statiques");
            System.out.println("clear-all : Supprimer tous les caches");
            System.out.println("stats : Afficher les statistiques actuelles du cache");
            System.out.println("help : Afficher ce message d'aide");
            System.out.println("exit : Arrêter le serveur");
            System.out.println("=======================\n");
        }
    }

    // Ajoutez ces variables et méthodes à la classe ServeurWeb
    private static volatile boolean isRunning = true;
    private static ServerSocket serverSocket;

    // Getters et setters pour les expirations
    public static void setDynamicPageExpiration(long expiration) {
        DYNAMIC_PAGE_EXPIRATION = expiration;
    }

    public static void setStaticPageExpiration(long expiration) {
        STATIC_PAGE_EXPIRATION = expiration;
    }

    public static long getDynamicPageExpiration() {
        return DYNAMIC_PAGE_EXPIRATION;
    }

    public static long getStaticPageExpiration() {
        return STATIC_PAGE_EXPIRATION;
    }

    // Méthode pour vider tous les caches
    public static void clearAllCaches() {
        USER_CACHES.clear();
    }

    // Méthode pour arrêter le serveur
    public static void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            cacheCleanupService.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}