package Group16.TrackoBus.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Group16.TrackoBus.backend.entity.Users;

@Repository
public interface UserRepo extends JpaRepository<Users, String> {

}
