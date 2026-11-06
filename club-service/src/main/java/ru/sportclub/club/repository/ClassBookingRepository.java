package ru.sportclub.club.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportclub.club.entity.ClassBooking;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ClassBookingRepository extends JpaRepository<ClassBooking, UUID> {
    Optional<ClassBooking> findByClubClass_IdAndMemberId(UUID classId, UUID memberId);

    Optional<ClassBooking> findByIdAndMemberId(UUID id, UUID memberId);

    long countByClubClass_IdAndStatus(UUID classId, String status);

    boolean existsByClubClass_Id(UUID classId);

    List<ClassBooking> findAllByClubClass_IdAndStatusOrderByBookedAt(UUID classId, String status);

    @EntityGraph(attributePaths = {"clubClass", "clubClass.trainer"})
    List<ClassBooking> findAllByMemberIdAndStatusOrderByClubClass_StartsAt(UUID memberId, String status);

    @Query("select b.clubClass.id from ClassBooking b where b.id = :id and b.memberId = :memberId")
    Optional<UUID> findOwnedClassId(@Param("id") UUID id, @Param("memberId") UUID memberId);

    @Query("""
            select b.clubClass.id as classId, count(b) as occupied
            from ClassBooking b where b.clubClass.id in :classIds and b.status = 'CONFIRMED'
            group by b.clubClass.id
            """)
    List<Occupancy> countConfirmedByClassIds(@Param("classIds") Collection<UUID> classIds);

    @Query("""
            select b.clubClass.id from ClassBooking b
            where b.clubClass.id in :classIds and b.memberId = :memberId and b.status = 'CONFIRMED'
            """)
    Set<UUID> findBookedClassIds(@Param("memberId") UUID memberId, @Param("classIds") Collection<UUID> classIds);

    interface Occupancy {
        UUID getClassId();
        long getOccupied();
    }
}
