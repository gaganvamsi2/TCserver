FROM tomcat:9.0.46
RUN mkdir -p /usr/local/tomcat/webapps/ords
COPY apache-tomcat-9.0.46/lib/. /usr/local/tomcat/bin
COPY apache-tomcat-9.0.46/webapps/app/.  /usr/local/tomcat/webapps/ords
RUN cd webapps/ords/WEB-INF/classes && javac -cp "../../../../bin/*" API_handler.java
EXPOSE 8080