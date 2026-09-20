import java.sql.*;

public class TypeProbe {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/commerce_service?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(url, "root", "15660872291");
             Statement st = c.createStatement()) {

            System.out.println("=== 1. 看 SQL 里各列的真实 JDBC 类型 ===");
            try (ResultSet rs = st.executeQuery("SELECT status, COUNT(*) AS n FROM t_order GROUP BY status")) {
                ResultSetMetaData md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    System.out.printf("  列%-8s  DB类型=%-12s  JDBC常量=%d  Java类=%s%n",
                            md.getColumnLabel(i), md.getColumnTypeName(i),
                            md.getColumnType(i), md.getColumnClassName(i));
                }
                System.out.println("  --- 逐行取值，打印 getObject() 的运行时类型 ---");
                while (rs.next()) {
                    Object status = rs.getObject("status");
                    Object n = rs.getObject("n");
                    System.out.printf("    status=%-14s (%s)   n=%-6s (%s)%n",
                            status, status.getClass().getSimpleName(),
                            n, n.getClass().getName());
                }
            }

            System.out.println();
            System.out.println("=== 2. getInt() vs getLong() 都能取吗 ===");
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS n FROM t_order")) {
                rs.next();
                System.out.println("    getInt()  = " + rs.getInt("n"));
                System.out.println("    getLong() = " + rs.getLong("n"));
            }

            System.out.println();
            System.out.println("=== 3. 带 WHERE 的（模拟 USER 越权条件）===");
            try (PreparedStatement ps = c.prepareStatement("SELECT status, COUNT(*) AS n FROM t_order WHERE user_id = ? GROUP BY status")) {
                ps.setLong(1, 1L);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.printf("    status=%-14s n=%s (%s)%n",
                                rs.getObject("status"), rs.getObject("n"), rs.getObject("n").getClass().getSimpleName());
                    }
                }
            }

            System.out.println();
            System.out.println("=== 4. UNION/空结果时 COUNT 返回什么 ===");
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS n FROM t_order WHERE user_id = 999999")) {
                rs.next();
                Object n = rs.getObject("n");
                System.out.println("    无匹配行时 COUNT(*) 的 n = " + n + " (" + n.getClass().getSimpleName() + ")  <- 不是 null，是 0");
            }
        }
    }
}
