package com.twentiethcenturygangsta.ourboard.site;

import com.twentiethcenturygangsta.ourboard.auth.Role;
import com.twentiethcenturygangsta.ourboard.auth.UserCredentials;
import com.twentiethcenturygangsta.ourboard.database.UserDatabaseCredentials;
import com.twentiethcenturygangsta.ourboard.trace.OurBoardEntity;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.*;
import java.util.ArrayList;
import java.util.Set;


@Slf4j
public class OurBoardClient {
    private final String ourBoardBasePackage = "com.twentiethcenturygangsta.ourboard";
    private final UserDatabaseCredentials userDatabaseCredentials;
    private final UserCredentials userCredentials;
    private final String basePackagePath;
    private Connection connection;
    private final Set<Class<?>> tables;
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Builder
    public OurBoardClient(UserDatabaseCredentials userDatabaseCredentials, UserCredentials userCredentials, String basePackagePath) throws Exception {
        this.userDatabaseCredentials = userDatabaseCredentials;
        this.userCredentials = userCredentials;
        this.basePackagePath = basePackagePath;
        this.tables = registerTables(basePackagePath);

        connectDB(userDatabaseCredentials);
        createAuthenticatedMember();
    }

    public UserCredentials getUserCredentials() {
        return userCredentials;
    }

    public Connection getConnection() {
        return connection;
    }

    public Set<Class<?>> getTables() {
        return tables;
    }

    public void connectDB(UserDatabaseCredentials userDatabaseCredentials) {
        try {
            Connection connection = DriverManager.getConnection(
                    userDatabaseCredentials.getUserDatabaseEndpoint(),
                    userDatabaseCredentials.getUserDatabaseId(),
                    userDatabaseCredentials.getUserDatabasePassword()
            );
            this.connection = connection;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Set<Class<?>> registerTables(String basePackagePath) {
        Set<Class<?>> baseClasses = new Reflections(ourBoardBasePackage).getTypesAnnotatedWith(OurBoardEntity.class);
        baseClasses.addAll(new Reflections(basePackagePath).getTypesAnnotatedWith(OurBoardEntity.class));
        return baseClasses;
    }

    public void createAuthenticatedMember() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS OurBoardMember (" +
                "id BIGINT NOT NULL AUTO_INCREMENT," +
                "memberId VARCHAR(100) NOT NULL," +
                "password VARCHAR(100) NOT NULL," +
                "role VARCHAR(100) NOT NULL," +
                "hasCreateAuthority boolean," +
                "hasReadAuthority boolean," +
                "hasUpdateAuthority boolean," +
                "hasDeleteAuthority boolean," +
                "PRIMARY KEY (id)" +
                ");";

        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.execute();

        if (!isExistAuthenticatedMember()) {
            createAuthenticatedSuperMember();
        }
    }

    @Deprecated
    public void register(ArrayList<Class> tables) throws SQLException {
        connectDB(userDatabaseCredentials);
        log.info("connection = OK");
    }

    public boolean isExistAuthenticatedMember() throws SQLException {
        String sql = "SELECT * FROM OurBoardMember WHERE memberId = " + String.format("'%s'", userCredentials.getMemberId());
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        ResultSet resultSet = preparedStatement.executeQuery();
        return resultSet.next();
    }

    public void createAuthenticatedSuperMember() throws Exception {
        String encodePassword = bCryptPasswordEncoder.encode(userCredentials.getPassword());

        String sql = String.format("INSERT INTO OurBoardMember (memberId, password, role, hasCreateAuthority, hasReadAuthority, hasUpdateAuthority, hasDeleteAuthority) " +
                "VALUES ('%s', '%s', '%s', true, true, true, true);", userCredentials.getMemberId(), encodePassword, Role.SUPER_USER);
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.execute();
    }
}
