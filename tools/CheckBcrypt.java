import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class CheckBcrypt {
  public static void main(String[] args) {
    String raw = "admin123";
    String hash = "$2a$10$kw4hWZy7KPjUDM2NbbJIpOnjiKLH/p9OAqHWodfSbniQhwEkbH612";
    System.out.println(new BCryptPasswordEncoder().matches(raw, hash));
  }
}
