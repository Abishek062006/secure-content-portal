import { Route, Routes } from 'react-router-dom';
import Nav from './components/Nav';
import { ProtectedRoute, AdminRoute } from './components/ProtectedRoute';
import Home from './pages/Home';
import Login from './pages/Login';
import Library from './pages/Library';
import ContentView from './pages/ContentView';
import Courses from './pages/Courses';
import CourseView from './pages/CourseView';
import LessonView from './pages/LessonView';
import AssessmentView from './pages/AssessmentView';
import AttemptView from './pages/AttemptView';
import NotFound from './pages/NotFound';
import Forbidden from './pages/Forbidden';
import ContentList from './pages/admin/ContentList';
import UploadContent from './pages/admin/UploadContent';
import EditContent from './pages/admin/EditContent';
import CourseList from './pages/admin/CourseList';
import NewCourse from './pages/admin/NewCourse';
import CourseEditor from './pages/admin/CourseEditor';
import QuestionBank from './pages/admin/QuestionBank';
import AssessmentResults from './pages/admin/AssessmentResults';
import Users from './pages/admin/Users';
import AuditLog from './pages/admin/AuditLog';

export default function App() {
  return (
    <>
      <Nav />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/forbidden" element={<Forbidden />} />

        <Route element={<ProtectedRoute />}>
          <Route path="/library" element={<Library />} />
          <Route path="/content/:id" element={<ContentView />} />
          <Route path="/courses" element={<Courses />} />
          <Route path="/courses/:id" element={<CourseView />} />
          <Route path="/courses/:courseId/lessons/:lessonId" element={<LessonView />} />
          <Route path="/courses/:courseId/assessments/:assessmentId" element={<AssessmentView />} />
          <Route path="/attempts/:attemptId" element={<AttemptView />} />
        </Route>

        <Route element={<AdminRoute />}>
          <Route path="/admin/content" element={<ContentList />} />
          <Route path="/admin/content/new" element={<UploadContent />} />
          <Route path="/admin/content/:id/edit" element={<EditContent />} />
          <Route path="/admin/courses" element={<CourseList />} />
          <Route path="/admin/courses/new" element={<NewCourse />} />
          <Route path="/admin/courses/:id/edit" element={<CourseEditor />} />
          <Route path="/admin/courses/:id/questions" element={<QuestionBank />} />
          <Route path="/admin/courses/:id/results" element={<AssessmentResults />} />
          <Route path="/admin/users" element={<Users />} />
          <Route path="/admin/audit" element={<AuditLog />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </>
  );
}
