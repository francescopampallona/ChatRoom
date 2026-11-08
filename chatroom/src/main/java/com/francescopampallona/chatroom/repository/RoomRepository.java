package com.francescopampallona.chatroom.repository;

import com.francescopampallona.chatroom.enums.RoomType;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository  extends JpaRepository<Room, Long> {

    List<Room> findByType(RoomType type);

    Optional<Room> findBySystemKey(String systemKey);

    @Query("""
      select r
      from Room r
      join RoomMember rm on rm.room = r
      join fetch r.owner
      where rm.user = :user""")
    List<Room> findRoomsByUser(@Param("user") User user);

}
