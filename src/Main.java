import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // Démarrer le serveur web dans un thread séparé
        Thread serverThread = new Thread(() -> {
            try {
                System.out.println("Démarrage du serveur web...");
                ServeurWeb.main(args);
            } catch (IOException e) {
                System.err.println("Erreur lors du démarrage du serveur : " + e.getMessage());
                e.printStackTrace();
            }
        });
        serverThread.start();

        // Attendre un court instant pour que le serveur démarre
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Lancer le navigateur dans le thread JavaFX
        javafx.application.Application.launch(Navigateur.class, args);
    }
}