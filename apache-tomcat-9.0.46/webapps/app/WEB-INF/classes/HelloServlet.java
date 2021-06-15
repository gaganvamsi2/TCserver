import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.util.Base64;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.sql.*;
import java.util.regex.*; 
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.jdbc.pool.OracleDataSource;
import oracle.ucp.admin.*;
import oracle.ucp.jdbc.*;
import java.text.SimpleDateFormat;  
import java.util.Date;  


@WebServlet("/*")
public class HelloServlet extends HttpServlet {
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String DB_USER=null,DB_PASSWORD=null,database=null;
       
        String pathInfo = request.getPathInfo();
        boolean  issql = Pattern.matches("^.*/_/sql",pathInfo);
        boolean  isplsql = Pattern.matches("^.*/_/plsql",pathInfo);
        System.out.println(pathInfo);
       // System.out.println("issql==   "+issql+"      isplsql==   "+isplsql);
        String[] pathParts = pathInfo.split("/");
        database = pathParts[1]; // {Schema alias}
        // check if the url is correct
        PrintWriter out = response.getWriter();
        try {
            if (!issql && !isplsql) {
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
                // For WINDOWS OS
                // String DB_URL="jdbc:oracle:thin:@"+database.toLowerCase()+"_medium?TNS_ADMIN=C:\\Users\\GagaN\\Desktop\\Intern Project\\POC2\\TCserver\\apache-tomcat-9.0.46\\webapps\\app\\WEB-INF\\wallets\\"+database;
                // For LINUX OS 
                String DB_URL="jdbc:oracle:thin:@"+database.toLowerCase()+"_medium?TNS_ADMIN=/usr/local/tomcat/webapps/ords/WEB-INF/wallets/"+database;
               
                // System.out.println(DB_URL);
                if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
                    // Authorization: Basic base64credentials
                    String base64Credentials = authorization.substring("Basic".length()).trim();
                    byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
                    String decodedString = new String(decodedBytes);
                    // credentials = username:password
                    final String[] values = decodedString.split(":", 2);
                    DB_USER = values[0];
                    DB_PASSWORD = values[1];
                }
                
                OracleDataSource ods = new OracleDataSource();
                ods.setURL(DB_URL);
                ods.setUser(DB_USER);
                ods.setPassword(DB_PASSWORD);
                Connection conn = ods.getConnection();
                System.out.println("Connection Successfull ............");
               
                //get the query from the body of the reque
                if(issql){
                    String query = null;
                    if ("POST".equalsIgnoreCase(request.getMethod())) {
                        query = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                    }

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
                else if(isplsql){

                   
                    String procedure = request.getHeader("procedure");
                    String call="{call "+procedure+"(";
                    int paramscount=Integer.parseInt(request.getHeader("paramscount"));
                    for(int i=0;i<paramscount-1;i++){
                        call=call+"?,";
                    }
                    if(paramscount>0){
                        call=call+"?)}";
                    }
                    else{
                        call=call+")}";
                    }
                   
                    CallableStatement cStmt = conn.prepareCall(call);
                   
                    for(int i=0;i<paramscount;i++){
                        String parameter = request.getHeader(String.valueOf(i+1));
                        String parametertype = request.getHeader(String.valueOf(i+1)+"_type");
                        
                        if("INT".equalsIgnoreCase(parametertype) || "number".equalsIgnoreCase(parametertype)){
                                cStmt.setInt(i+1, Integer.parseInt(parameter));
                               
                        }
                        else if("varchar".equalsIgnoreCase(parametertype)){
                            cStmt.setString(i+1, parameter);
                            
                        } 
                  
                        else if("date".equalsIgnoreCase(parametertype)){
                            java.util.Date javaDatetime = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(parameter);
                            Timestamp jdbcDatetime = new Timestamp(javaDatetime.getTime());
                            
                            cStmt.setTimestamp(i+1, jdbcDatetime);
                            
                        }
                        else if("bool".equalsIgnoreCase(parametertype) || "boolean".equalsIgnoreCase(parametertype) ){
                            cStmt.setBoolean(i+1, Boolean.parseBoolean(parameter));
                           
                        }
                        else{
                            
                        }
                    }
                    boolean hadResults = cStmt.execute();
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    JSONObject obj = new JSONObject();
                    obj.put("Success", "Executed the procedure "+procedure);
                    out.print(obj);
                    out.flush();
                    conn.close();
                }                
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
        }
        System.out.println("Servlet is called");
        out.close();

    }
}