package com.example.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HazelcastSessionTest {

    @Test
    void createSession_shouldGenerateUniqueId() {
        HazelcastSession session1 = HazelcastSession.create();
        HazelcastSession session2 = HazelcastSession.create();

        assertNotNull(session1.getId());
        assertNotNull(session2.getId());
        assertNotEquals(session1.getId(), session2.getId());
    }

    @Test
    void createSession_shouldHaveDefaultTimeout() {
        HazelcastSession session = HazelcastSession.create();
        assertEquals(Duration.ofMinutes(30), session.getMaxInactiveInterval());
    }

    @Test
    void setAttribute_shouldStoreValue() {
        HazelcastSession session = HazelcastSession.create();
        session.setAttribute("user", "testUser");
        assertEquals("testUser", session.getAttribute("user"));
    }

    @Test
    void setAttribute_withNull_shouldRemoveAttribute() {
        HazelcastSession session = HazelcastSession.create();
        session.setAttribute("user", "testUser");
        session.setAttribute("user", null);
        assertNull(session.getAttribute("user"));
    }

    @Test
    void changeSessionId_shouldReturnNewId() {
        HazelcastSession session = HazelcastSession.create();
        String originalId = session.getId();
        String newId = session.changeSessionId();

        assertNotEquals(originalId, newId);
        assertEquals(newId, session.getId());
        assertEquals(originalId, session.getOriginalId());
    }

    @Test
    void isExpired_shouldReturnFalse_whenNotExpired() {
        HazelcastSession session = HazelcastSession.create();
        assertFalse(session.isExpired());
    }

    @Test
    void isChanged_shouldTrackModifications() {
        HazelcastSession session = HazelcastSession.create();
        assertTrue(session.isChanged()); // New session is marked as changed

        session.clearChanged();
        assertFalse(session.isChanged());

        session.setAttribute("key", "value");
        assertTrue(session.isChanged());
    }

    @Test
    void getAttribute_withList_shouldReturnTrackedList() {
        HazelcastSession session = HazelcastSession.create();
        List<String> items = new ArrayList<>();
        items.add("item1");
        session.setAttribute("items", items);

        session.clearChanged();
        assertFalse(session.isChanged());

        // Get the list and modify it
        List<String> trackedItems = session.getAttribute("items");
        trackedItems.add("item2");

        // Session should be marked as changed
        assertTrue(session.isChanged());
    }

    @Test
    void getAttribute_withMap_shouldReturnTrackedMap() {
        HazelcastSession session = HazelcastSession.create();
        Map<String, String> data = new HashMap<>();
        data.put("key1", "value1");
        session.setAttribute("data", data);

        session.clearChanged();
        assertFalse(session.isChanged());

        // Get the map and modify it
        Map<String, String> trackedData = session.getAttribute("data");
        trackedData.put("key2", "value2");

        // Session should be marked as changed
        assertTrue(session.isChanged());
    }

    @Test
    void getAttribute_withNestedCollection_shouldTrackChanges() {
        HazelcastSession session = HazelcastSession.create();

        // Create nested structure: List<Map<String, String>>
        List<Map<String, String>> nestedList = new ArrayList<>();
        Map<String, String> innerMap = new HashMap<>();
        innerMap.put("innerKey", "innerValue");
        nestedList.add(innerMap);

        session.setAttribute("nested", nestedList);
        session.clearChanged();
        assertFalse(session.isChanged());

        // Get nested structure and modify inner map
        List<Map<String, String>> trackedList = session.getAttribute("nested");
        Map<String, String> trackedInnerMap = trackedList.get(0);
        trackedInnerMap.put("newKey", "newValue");

        // Session should be marked as changed
        assertTrue(session.isChanged());
    }
}
