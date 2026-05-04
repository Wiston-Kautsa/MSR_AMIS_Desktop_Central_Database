import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class CheckBcrypt2 {
  public static void main(String[] args) {
    String raw = "Kautsa123!";
    String hash = "$2a$10$IR3.l4lciZ0/Y8www.c7Ren8laJhAEMeeIhlkXxLnvGxwQoSekuKi";
    System.out.println(new BCryptPasswordEncoder().matches(raw, hash));
  }
}
