package com.francescopampallona.chatroom.repository;

import com.francescopampallona.chatroom.model.RoomMember;
import com.francescopampallona.chatroom.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMemberRepository  extends JpaRepository<RoomMember, Long> {
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    @Query("select membership from RoomMember membership join fetch membership.user agent "
            + "where membership.room.id = :roomId and agent.agentKey is not null")
    List<RoomMember> findAgentMembershipsByRoomId(@Param("roomId") Long roomId);
}
