import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class Navigateur extends Application {
    private static final String BASE_URL = "http://127.0.0.1:1567/";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Navigateur Web");
        BorderPane root = new BorderPane();

        // Création du TabPane pour gérer les onglets
        TabPane tabPane = new TabPane();
        root.setCenter(tabPane);

        // Ajouter un onglet initial
        Tab tab = createTab(BASE_URL);
        tabPane.getTabs().add(tab);

        // Ajouter un bouton pour ouvrir un nouvel onglet
        Button addTabButton = new Button("Nouveau Onglet");
        addTabButton.setOnAction(event -> {
            Tab newTab = createTab(BASE_URL);
            tabPane.getTabs().add(newTab);
            tabPane.getSelectionModel().select(newTab);  // Sélectionner le nouvel onglet
        });

        // Ajouter le nouveau bouton pour la gestion du cache
        Button cacheButton = new Button("Gestion Cache");
        cacheButton.setOnAction(event -> {
            Tab newTab = createTab("http://127.0.0.1:1567/cache/list");
            tabPane.getTabs().add(newTab);
            tabPane.getSelectionModel().select(newTab);
        });

        // Barre d'outils principale
        HBox topBar = new HBox(10);
        topBar.setStyle("-fx-padding: 10;");
        topBar.getChildren().addAll(addTabButton, cacheButton);  // Ajout du nouveau bouton
        root.setTop(topBar);

        // Création de la scène
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    // Méthode pour créer un onglet avec un URL et un WebView
    private Tab createTab(String url) {
        // Création du champ de recherche et du bouton dans l'onglet
        TextField urlField = new TextField(url);
        Button goButton = new Button("GO");

        // Bouton pour le mode sombre
        Button darkModeButton = new Button("Mode Sombre");
        final boolean[] darkMode = {false};

        // HBox pour le champ de recherche, le bouton "GO" et le bouton "Mode Sombre"
        HBox tabBar = new HBox(10);
        tabBar.setStyle("-fx-padding: 10;");
        HBox.setHgrow(urlField, Priority.ALWAYS);
        tabBar.getChildren().addAll(urlField, goButton, darkModeButton);

        // WebView et WebEngine
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();
        webEngine.load(url);

        webEngine.locationProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Extraire la partie relative de l'URL (après le port)
                String relativeUrl = newValue;
                if (newValue.startsWith("http://127.0.0.1:1567/") || newValue.startsWith("http://localhost:1567/")) {
                    relativeUrl = newValue;
                }
                urlField.setText(relativeUrl);
            }
        });

        // Action pour charger l'URL
        goButton.setOnAction(event -> {
            String urlInput = urlField.getText().trim();
            if (!urlInput.startsWith("http://127.0.0.1:1567") && !urlInput.startsWith("http://localhost:1567")) {
                urlInput = "http://127.0.0.1:1567" + (urlInput.startsWith("/") ? "" : "/") + urlInput;
            }
            System.out.println("Chargement de l'URL : " + urlInput);
            webEngine.load(urlInput);
        });

        // Action pour basculer le mode sombre pour cet onglet
        darkModeButton.setOnAction(event -> {
            darkMode[0] = !darkMode[0];
            if (darkMode[0]) {
                webEngine.executeScript("document.body.style.backgroundColor = '#121212';");
                webEngine.executeScript("document.body.style.color = 'white';");
            } else {
                webEngine.executeScript("document.body.style.backgroundColor = 'white';");
                webEngine.executeScript("document.body.style.color = 'black';");
            }
        });

        // Créer l'onglet avec le champ de recherche, le bouton, et le WebView
        BorderPane tabContent = new BorderPane();
        tabContent.setTop(tabBar);
        tabContent.setCenter(webView);

        Tab tab = new Tab("Nouvel Onglet");
        tab.setClosable(true);
        tab.setContent(tabContent);

        return tab;
    }
}
