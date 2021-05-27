import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.util.Enumeration;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.sql.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;



@WebServlet("/*")
public class HelloServlet extends HttpServlet {
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String user=null,pass=null,database=null;
        String pathInfo = request.getPathInfo();
        String[] pathParts = pathInfo.split("/");
        database = pathParts[1]; // {Schema alias}
        // check if the url is correct
        PrintWriter out = response.getWriter();
        try {
            if (!pathParts[2].equals("_") || !pathParts[3].equals("sql")) {
                response.setStatus(400);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("error", "check the URL");
                    response.getWriter().print(obj);
                }
                catch(Exception exception) {
                    System.out.println("Error: " + exception.getMessage());
                }
            }
            else {
                // retrieve the auth details to connect to database
                final String authorization = request.getHeader("Authorization");
                // **** try catch here


                if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
                    // Authorization: Basic base64credentials
                    String base64Credentials = authorization.substring("Basic".length()).trim();
                    byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
                    String decodedString = new String(decodedBytes);
                    // credentials = username:password
                    final String[] values = decodedString.split(":", 2);
                    user = values[0];
                    pass = values[1];
                }

                //get the query from the body of the reque

                String query = null;
                if ("POST".equalsIgnoreCase(request.getMethod())) {
                    query = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                }


                // Step 1: Allocate a database 'Connection' object
                Class.forName("com.mysql.jdbc.Driver");
                Connection conn = DriverManager.getConnection("jdbc:mysql://host.docker.internal:3306/" + database, user, pass);   // For MySQL
                // Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/" + database, user, pass);   // For MySQL
                // The format is: "jdbc:mysql://hostname:port/databaseName", "username", "password"
                // Step 2: Allocate a 'Statement' object in the Connection
                Statement stmt = conn.createStatement();
                // Step 3: Execute SQL  query
                ResultSet rset = stmt.executeQuery(query);  // Send the query to the server

                JSONArray json = new JSONArray();
                ResultSetMetaData rsmd = rset.getMetaData();
                while (rset.next()) {
                    int numColumns = rsmd.getColumnCount();
                    JSONObject obj = new JSONObject();
                    for (int i = 1; i <= numColumns; i++) {
                        String column_name = rsmd.getColumnName(i);
                        obj.put(column_name, rset.getObject(column_name));
                    }
                    json.put(obj);
                }
                response.setStatus(200);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                out.print(json);
                out.flush();
                conn.close();
            }
        } catch(Exception ex) {
           // response.sendError(response.SC_BAD_REQUEST,ex.getMessage() );
            response.setStatus(400);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try {
                JSONObject obj = new JSONObject();
                obj.put("error", ex.getMessage());
                response.getWriter().print(obj);
            }
            catch(Exception exception) {
                System.out.println("Error: " + exception.getMessage());
            }
            //System.out.println("<p>Error: " + ex.getMessage() + "</p>");
            // System.out.println("<p>Check Tomcat console for details.</p>");
            // ex.printStackTrace();
        }
        System.out.println("Servlet is called");
        out.close();

    }
}