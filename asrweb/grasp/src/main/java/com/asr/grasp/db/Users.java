package com.asr.grasp.db;

import com.sun.org.apache.xpath.internal.operations.Bool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Users extends Base {

    /**
     * Tells us where we can expect each value for the results from the
     * database.
     */
    final int idIdx = 1;
    final int usernameIdx = 2;
    final int passwordIdx = 3;

    final String idCol = "id";
    final String usernameCol = "username";
    final String passwordCol = "password";

    public Users() {

    }

    /**
     * Creates a new user account. Encrypts password using bCrypt algorithm.
     * Saves the user to the database.
     *
     * @param account
     * @return User
     */
    @Override
    public User registerNewUserAccount(User account) {
        User user = new User();
        user.setId(generateId());
        user.setUsername(account.getUsername());
        user.setPassword(encryptPassword(account.getPassword()));
        return saveNewUser(user);
    }

    /**
     * Generates a userId.
     * ToDo: Convert the UserId in the table to be UID rather than a long.
     * Currently using:
     * https://stackoverflow.com/questions/15184820/how-to-generate-unique-long-using-uuid
     * This will gaurentee uniqueness.
     */
    private Long generateId() {
        return (System.currentTimeMillis() << 20) |
                (System.nanoTime() & ~9223372036854251520L);
    }

    /**
     * Generates a hash and encrypts the users password.
     * https://en.wikipedia.org/wiki/Bcrypt.
     *
     * @param password
     * @return hashed password
     */
    private String encryptPassword(String password) {
        String encryptedPassword = BCrypt.hashpw(password, BCrypt
                .gensalt());
        return encryptedPassword;
    }

    /**
     * Resets the users password.
     * ToDo: Need to update to use a key generated by an email.
     *
     * @return
     */
    public Boolean resetPassword(int id, String newPassword) {
        try {
            ResultSet user = updateStringOnId("UPDATE USERS(password) VALUES" +
                    "(?) WHERE id=?;", id, newPassword);
            if (user.next()) {
                return true;
            }
            return false;
        } catch (Exception e) {

        }
        return user;
    }

    /**
     * Checks the username and password are valid.
     *
     * @return
     */
    public int getUserId(String username, String rawPassword) {
        try {
            // Find the user by username in the database
            ResultSet user = queryOnString("SELECT * FROM USERS WHERE " +
                    " username=?;", username);

            // Update the users password if we have been given the override
            String encryptedPassword = user.getString(passwordIdx);

            // Check the inputted username against the encrypted password
            // needs to be in a try catch at the moment as we have to change the
            // users' passwords from plain text to encrypted.

            Boolean matches = BCrypt.checkpw(rawPassword, encryptedPassword);

            if (matches == true) {
                return user.getInt(idIdx);
            }
        } catch (Exception e) {
            System.out.println(e);
            return -1;
        }

        return -1;
    }

}
