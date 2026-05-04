import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ProbeLookup {
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        boolean delete = args.length > 0 && "delete".equalsIgnoreCase(args[0]);

        List<String> targets = new ArrayList<>(List.of(
                "C:/Users/wkaut/AppData/Local/MSR AMIS/msr_amis.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_pre_restore_wkautsa_gmail.com_2026-04-20_11-23.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_pre_restore_wkautsa_gmail.com_2026-04-20_22-24.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_pre_restore_wkautsa_gmail.com_2026-04-20_22-29.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_submission_wkautsa_gmail.com_2026-04-20_11-23.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_submission_wkautsa_gmail.com_2026-04-20_22-22.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_submission_wkautsa_gmail.com_2026-04-20_22-29.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_submission_wkautsa_gmail.com_2026-04-20_23-07.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_submission_wkautsa_gmail.com_2026-04-21_10-55.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_submission_wkautsa_gmail.com_2026-04-21_11-01.db",
                "C:/Users/wkaut/Documents/MSR-AMIS/backups/msr_amis_submission_wkautsa_gmail.com_2026-04-21_13-00.db"
        ));
        addDirectoryDatabases(targets, "G:/My Drive/MSR-AMIS-DATA/OFFICIAL");
        addDirectoryDatabases(targets, "G:/My Drive/MSR-AMIS-DATA/SUBMISSIONS");

        for (String target : targets) {
            Path path = Path.of(target);
            if (!Files.exists(path)) {
                continue;
            }
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + target)) {
                int userCount = safeCount(conn,
                        "SELECT COUNT(*) FROM users WHERE lower(coalesce(username,''))='probe@local' OR lower(coalesce(email,''))='probe@local'");
                int auditCount = safeCount(conn,
                        "SELECT COUNT(*) FROM audit_log WHERE lower(coalesce(username,''))='probe@local' OR lower(coalesce(performed_by,''))='probe@local'");
                if (delete) {
                    if (auditCount > 0) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM audit_log WHERE lower(coalesce(username,''))='probe@local' OR lower(coalesce(performed_by,''))='probe@local'"
                        )) {
                            int deleted = ps.executeUpdate();
                            System.out.println("DELETED " + deleted + " audit rows from " + path);
                        }
                        auditCount = safeCount(conn,
                                "SELECT COUNT(*) FROM audit_log WHERE lower(coalesce(username,''))='probe@local' OR lower(coalesce(performed_by,''))='probe@local'");
                    }
                    if (userCount > 0) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM users WHERE lower(coalesce(username,''))='probe@local' OR lower(coalesce(email,''))='probe@local'"
                        )) {
                            int deleted = ps.executeUpdate();
                            System.out.println("DELETED " + deleted + " user rows from " + path);
                        }
                        userCount = safeCount(conn,
                                "SELECT COUNT(*) FROM users WHERE lower(coalesce(username,''))='probe@local' OR lower(coalesce(email,''))='probe@local'");
                    }
                }
                if (userCount > 0 || auditCount > 0) {
                    System.out.println(path + " | users=" + userCount + " | audit=" + auditCount);
                }
            }
        }
    }

    private static int count(Connection conn, String sql) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static int safeCount(Connection conn, String sql) {
        try {
            return count(conn, sql);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void addDirectoryDatabases(List<String> targets, String directory) {
        Path root = Paths.get(directory);
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".db"))
                    .map(Path::toString)
                    .forEach(targets::add);
        } catch (Exception ignored) {
        }
    }
}
