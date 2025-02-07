package teammates.ui.webapi;

import java.util.ArrayList;
import java.util.List;

import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.datatransfer.attributes.StudentAttributes;
import teammates.common.exception.SearchServiceException;
import teammates.common.util.Const;
import teammates.storage.sqlentity.Instructor;
import teammates.storage.sqlentity.Student;
import teammates.ui.output.StudentData;
import teammates.ui.output.StudentsData;

/**
 * Action for searching for students.
 */
public class SearchStudentsAction extends Action {

    @Override
    AuthType getMinAuthLevel() {
        return AuthType.LOGGED_IN;
    }

    @Override
    void checkSpecificAccessControl() throws UnauthorizedAccessException {
        // Only instructors and admins can search for student
        if (!userInfo.isInstructor && !userInfo.isAdmin) {
            throw new UnauthorizedAccessException("Instructor or Admin privilege is required to access this resource.");
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingNPE") // see this [PR](https://github.com/TEAMMATES/teammates/pull/12728/files)
    @Override
    public JsonResult execute() {
        String searchKey = getNonNullRequestParamValue(Const.ParamsNames.SEARCH_KEY);
        String entity = getNonNullRequestParamValue(Const.ParamsNames.ENTITY_TYPE);

        List<Student> students;

        try {
            if (userInfo.isInstructor && entity.equals(Const.EntityType.INSTRUCTOR)) {
                List<Instructor> instructors = sqlLogic.getInstructorsForGoogleId(userInfo.id);
                students = sqlLogic.searchStudents(searchKey, instructors);
            } else if (userInfo.isAdmin && entity.equals(Const.EntityType.ADMIN)) {
                students = sqlLogic.searchStudentsInWholeSystem(searchKey);
            } else {
                throw new InvalidHttpParameterException("Invalid entity type for search");
            }
        } catch (SearchServiceException e) {
            return new JsonResult(e.getMessage(), e.getStatusCode());
        } catch (NullPointerException e) {
            // Solr search service is not active
            students = new ArrayList<>();
        }

        // Search in datastore. For more information on dual db support, see this [PR](https://github.com/TEAMMATES/teammates/pull/12728/files)
        List<StudentAttributes> studentsDatastore;
        try {
            if (userInfo.isInstructor && entity.equals(Const.EntityType.INSTRUCTOR)) {
                List<InstructorAttributes> instructors = logic.getInstructorsForGoogleId(userInfo.id);
                studentsDatastore = logic.searchStudents(searchKey, instructors);
            } else if (userInfo.isAdmin && entity.equals(Const.EntityType.ADMIN)) {
                studentsDatastore = logic.searchStudentsInWholeSystem(searchKey);
            } else {
                throw new InvalidHttpParameterException("Invalid entity type for search");
            }
        } catch (SearchServiceException e) {
            return new JsonResult(e.getMessage(), e.getStatusCode());
        } catch (NullPointerException e) {
            // Solr search service is not active
            studentsDatastore = new ArrayList<>();
        }

        List<StudentData> studentDataList = new ArrayList<>();
        // Add students from sql database
        for (Student s : students) {
            StudentData studentData = new StudentData(s);

            if (userInfo.isAdmin && entity.equals(Const.EntityType.ADMIN)) {
                studentData.addAdditionalInformationForAdminSearch(
                        s.getRegKey(),
                        sqlLogic.getCourseInstitute(s.getCourseId()),
                        s.getGoogleId()
                );
            }

            studentDataList.add(studentData);
        }
        // Add students from datastore
        for (StudentAttributes s : studentsDatastore) {
            StudentData studentData = new StudentData(s);

            if (userInfo.isAdmin && entity.equals(Const.EntityType.ADMIN)) {
                studentData.addAdditionalInformationForAdminSearch(
                        s.getKey(),
                        logic.getCourseInstitute(s.getCourse()),
                        s.getGoogleId()
                );
            }
            // If the course has been migrated, then the student would have been added already
            if (isCourseMigrated(studentData.getCourseId())) {
                continue;
            }

            studentDataList.add(studentData);
        }
        StudentsData studentsData = new StudentsData();
        studentsData.setStudents(studentDataList);

        return new JsonResult(studentsData);
    }
}
