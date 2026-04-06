package dev.rafex.insightbloom.cli;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * InsightBloom CLI — crea o actualiza usuarios en la base de datos SQLite del servicio users.
 *
 * Uso:
 *   java -jar insightbloom-cli.jar create-user \
 *       --db /ruta/a/users.db \
 *       --username john \
 *       --password secreto123 \
 *       --role ORGANIZER \
 *       [--display-name "John Doe"] \
 *       [--email john@example.com]
 *
 * Roles disponibles: ORGANIZER, MODERATOR
 * Si el usuario ya existe, actualiza su password y role.
 */
public class CreateUserCli {

    public static void main(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printHelp();
            System.exit(0);
        }

        if (!"create-user".equals(args[0])) {
            System.err.println("Comando desconocido: " + args[0]);
            System.err.println("Comandos disponibles: create-user");
            System.exit(1);
        }

        // Parse arguments
        String db          = "users.db";
        String username    = null;
        String password    = null;
        String role        = "ORGANIZER";
        String displayName = null;
        String email       = null;

        for (int i = 1; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--db"           -> db          = args[++i];
                case "--username"     -> username    = args[++i];
                case "--password"     -> password    = args[++i];
                case "--role"         -> role        = args[++i].toUpperCase();
                case "--display-name" -> displayName = args[++i];
                case "--email"        -> email       = args[++i];
                default -> {
                    System.err.println("Argumento desconocido: " + args[i]);
                    System.exit(1);
                }
            }
        }

        // Validate required
        if (username == null || username.isBlank()) {
            die("--username es requerido");
        }
        if (password == null || password.isBlank()) {
            die("--password es requerido");
        }
        if (!role.equals("ORGANIZER") && !role.equals("MODERATOR")) {
            die("--role debe ser ORGANIZER o MODERATOR");
        }

        if (displayName == null) displayName = username;

        // Execute
        try {
            createUser(db, username, password, role, displayName, email);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(1);
        }
    }

    static void createUser(String dbPath, String username, String password,
                           String role, String displayName, String email) throws Exception {

        String url = "jdbc:sqlite:" + dbPath;
        try (Connection conn = DriverManager.getConnection(url)) {

            // Ensure table and password_hash column exist
            ensureSchema(conn);

            String passwordHash = sha256(password);
            String now = Instant.now().toString();

            // Check if user already exists
            String existingUuid = findExistingUuid(conn, username);

            if (existingUuid != null) {
                // Update existing user
                String sql = """
                    UPDATE users SET role=?, password_hash=?, display_name=?, email=?, updated_at=?
                    WHERE username=?
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, role);
                    ps.setString(2, passwordHash);
                    ps.setString(3, displayName);
                    ps.setString(4, email);
                    ps.setString(5, now);
                    ps.setString(6, username);
                    ps.executeUpdate();
                }
                System.out.println("✓ Usuario actualizado");
                System.out.println("  uuid:     " + existingUuid);
                System.out.println("  username: " + username);
                System.out.println("  role:     " + role);
            } else {
                // Insert new user
                String uuid = UUID.randomUUID().toString();
                String sql = """
                    INSERT INTO users (uuid, username, display_name, email, role, status, password_hash, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid);
                    ps.setString(2, username);
                    ps.setString(3, displayName);
                    ps.setString(4, email);
                    ps.setString(5, role);
                    ps.setString(6, passwordHash);
                    ps.setString(7, now);
                    ps.setString(8, now);
                    ps.executeUpdate();
                }
                System.out.println("✓ Usuario creado");
                System.out.println("  uuid:     " + uuid);
                System.out.println("  username: " + username);
                System.out.println("  role:     " + role);
            }
            System.out.println("  db:       " + dbPath);
        }
    }

    private static void ensureSchema(Connection conn) throws Exception {
        try (var stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    username TEXT NOT NULL UNIQUE,
                    display_name TEXT,
                    email TEXT,
                    role TEXT NOT NULL,
                    status TEXT NOT NULL,
                    password_hash TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
            // Migración segura para DBs existentes sin la columna
            try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN password_hash TEXT"); }
            catch (Exception ignored) {}
        }
    }

    private static String findExistingUuid(Connection conn, String username) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM users WHERE username=?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("uuid") : null;
        }
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static void die(String msg) {
        System.err.println("[ERROR] " + msg);
        printHelp();
        System.exit(1);
    }

    private static void printHelp() {
        System.out.println("""
            InsightBloom CLI — Administración de usuarios

            COMANDOS:
              create-user    Crea o actualiza un usuario en la base de datos

            USO:
              java -jar insightbloom-cli.jar create-user [opciones]

            OPCIONES:
              --db           <ruta>    Ruta al archivo users.db  (default: users.db)
              --username     <texto>   Nombre de usuario         (requerido)
              --password     <texto>   Contraseña                (requerido)
              --role         <rol>     ORGANIZER | MODERATOR     (default: ORGANIZER)
              --display-name <texto>   Nombre visible            (default: username)
              --email        <texto>   Correo electrónico        (opcional)

            EJEMPLOS:
              # Crear un organizador
              java -jar insightbloom-cli.jar create-user \\
                --username rafex --password mipass123 --role ORGANIZER --email rafex@example.com

              # Crear un moderador apuntando a un DB específico
              java -jar insightbloom-cli.jar create-user \\
                --db /data/users.db --username maria --password abc --role MODERATOR

              # Actualizar contraseña de admin existente
              java -jar insightbloom-cli.jar create-user \\
                --username admin --password nueva-clave-segura --role ORGANIZER
            """);
    }
}
