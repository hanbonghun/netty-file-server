package org.example;

public class Main {

    public static void main(String[] args) throws Exception {
        String uploadDir = "C:/uploads";
        int port = 8080;

        FileServer server = new FileServer(port, uploadDir);
        server.start();
    }
}