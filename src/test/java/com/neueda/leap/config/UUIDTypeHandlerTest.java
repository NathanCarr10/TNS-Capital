package com.neueda.leap.config;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UUIDTypeHandler converts between UUID and its text form")
class UUIDTypeHandlerTest {
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final UUIDTypeHandler handler = new UUIDTypeHandler();

    @Test
    @DisplayName("Writes a UUID parameter as text")
    void writesUuidAsText() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setNonNullParameter(ps, 1, ID, JdbcType.VARCHAR);

        verify(ps).setString(1, ID.toString());
    }

    @Test
    @DisplayName("Reads a UUID by column name, and null as null")
    void readsByColumnName() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn(ID.toString());
        when(rs.getString("missing")).thenReturn(null);

        assertEquals(ID, handler.getNullableResult(rs, "id"));
        assertNull(handler.getNullableResult(rs, "missing"));
    }

    @Test
    @DisplayName("Reads a UUID by column index, and null as null")
    void readsByColumnIndex() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(ID.toString());
        when(rs.getString(2)).thenReturn(null);

        assertEquals(ID, handler.getNullableResult(rs, 1));
        assertNull(handler.getNullableResult(rs, 2));
    }

    @Test
    @DisplayName("Reads a UUID from a stored procedure result, and null as null")
    void readsFromCallableStatement() throws SQLException {
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(1)).thenReturn(ID.toString());
        when(cs.getString(2)).thenReturn(null);

        assertEquals(ID, handler.getNullableResult(cs, 1));
        assertNull(handler.getNullableResult(cs, 2));
    }
}
