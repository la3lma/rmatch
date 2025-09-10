import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple PostgreSQL connection test for the prospects dockerized test environment. This test is
 * completely independent of the main rmatch codebase.
 */
public class PostgresTest {

  private static final String DB_URL = "jdbc:postgresql://localhost:5433/prospects_test";
  private static final String DB_USER = "testuser";
  private static final String DB_PASSWORD = "testpass";

  public static void main(String[] args) {
    System.out.println("🧪 Starting Postgres Test for Prospects Environment");

    try {
      // Load PostgreSQL JDBC driver
      Class.forName("org.postgresql.Driver");
      System.out.println("✅ PostgreSQL JDBC driver loaded successfully");

      // Test database connection
      testDatabaseConnection();

      // Test basic CRUD operations
      testBasicOperations();

      // Test regex pattern operations
      testRegexPatterns();

      System.out.println("🎉 All Postgres tests completed successfully!");

    } catch (Exception e) {
      System.err.println("❌ Test failed with error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void testDatabaseConnection() throws SQLException {
    System.out.println("\n🔌 Testing database connection...");

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
      if (conn != null && !conn.isClosed()) {
        System.out.println("✅ Database connection established successfully");

        DatabaseMetaData meta = conn.getMetaData();
        System.out.println(
            "📊 Database: "
                + meta.getDatabaseProductName()
                + " "
                + meta.getDatabaseProductVersion());
      }
    }
  }

  private static void testBasicOperations() throws SQLException {
    System.out.println("\n📝 Testing basic CRUD operations...");

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

      // Test SELECT operation
      String selectQuery = "SELECT id, name, value FROM test_data ORDER BY id";
      try (PreparedStatement stmt = conn.prepareStatement(selectQuery);
          ResultSet rs = stmt.executeQuery()) {

        int recordCount = 0;
        System.out.println("📋 Existing test data:");
        while (rs.next()) {
          recordCount++;
          System.out.printf(
              "  ID: %d, Name: %s, Value: %d%n",
              rs.getInt("id"), rs.getString("name"), rs.getInt("value"));
        }
        System.out.println("✅ Found " + recordCount + " records in test_data table");
      }

      // Test INSERT operation
      String insertQuery = "INSERT INTO test_data (name, value) VALUES (?, ?) RETURNING id";
      try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
        stmt.setString(1, "dynamic_test_" + System.currentTimeMillis());
        stmt.setInt(2, 12345);

        try (ResultSet rs = stmt.executeQuery()) {
          if (rs.next()) {
            int newId = rs.getInt("id");
            System.out.println("✅ Successfully inserted new record with ID: " + newId);
          }
        }
      }
    }
  }

  private static void testRegexPatterns() throws SQLException {
    System.out.println("\n🔍 Testing regex patterns table operations...");

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

      // Fetch active regex patterns
      String selectQuery = "SELECT pattern, description FROM regex_patterns WHERE is_active = true";
      try (PreparedStatement stmt = conn.prepareStatement(selectQuery);
          ResultSet rs = stmt.executeQuery()) {

        System.out.println("🎯 Active regex patterns:");
        List<String> patterns = new ArrayList<>();
        while (rs.next()) {
          String pattern = rs.getString("pattern");
          String description = rs.getString("description");
          patterns.add(pattern);
          System.out.printf("  Pattern: %-20s Description: %s%n", pattern, description);
        }

        System.out.println("✅ Loaded " + patterns.size() + " active regex patterns");

        // Simple pattern matching test against test strings
        testPatternMatching(patterns);
      }
    }
  }

  private static void testPatternMatching(List<String> patterns) {
    System.out.println("\n🎲 Testing pattern matching...");

    String[] testStrings = {
      "hello beautiful world",
      "test_123_data",
      "abc123def456",
      "pattern matching example",
      "regex is powerful"
    };

    for (String testStr : testStrings) {
      System.out.println("Testing string: '" + testStr + "'");
      for (String pattern : patterns) {
        try {
          if (testStr.matches(pattern)) {
            System.out.println("  ✅ Matches pattern: " + pattern);
          }
        } catch (Exception e) {
          // Some patterns might not be valid Java regex
          System.out.println("  ⚠️ Pattern '" + pattern + "' caused error: " + e.getMessage());
        }
      }
    }

    System.out.println("✅ Pattern matching test completed");
  }
}
