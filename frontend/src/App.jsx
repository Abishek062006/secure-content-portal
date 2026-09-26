import { Route, Routes, useMatch } from 'react-router-dom';
import Nav from './components/Nav';
import { ProtectedRoute, AdminRoute } from './components/ProtectedRoute';
import Home from './pages/Home';
import Login from './pages/Login';
import Courses from './pages/Courses';
import CourseView from './pages/CourseView';
import LessonView from './pages/LessonView';
import MaterialView from './pages/MaterialView';
import AssessmentView from './pages/AssessmentView';
import AttemptView from './pages/AttemptView';
import MyLearning from './pages/MyLearning';
import VerifyCertificate from './pages/VerifyCertificate';
import Feed from './pages/Feed';
import Profile from './pages/Profile';
import NotFound from './pages/NotFound';
import Forbidden from './pages/Forbidden';
import CourseList from './pages/admin/CourseList';
import Dashboard from './pages/admin/Dashboard';
import NewCourse from './pages/admin/NewCourse';
import CourseEditor from './pages/admin/CourseEditor';
import QuestionBank from './pages/admin/QuestionBank';
import AssessmentResults from './pages/admin/AssessmentResults';
import Users from './pages/admin/Users';
import AuditLog from './pages/admin/AuditLog';
import AdminLeaderboard from './pages/admin/AdminLeaderboard';

import Leaderboard from './pages/Leaderboard';
import Hackathons from './pages/Hackathons';
import MockInterviews from './pages/MockInterviews';
import AdminHackathons from './pages/admin/AdminHackathons';
import AdminMockInterviews from './pages/admin/AdminMockInterviews';

export default function App() {
  // Lessons and resources use their own focused player bar instead of the site navigation.
  // Both hooks always run (never `a || b`): skipping one changes the hook order between routes and crashes the app.
  const onLesson = useMatch('/courses/:courseId/lessons/:lessonId');
  const onMaterial = useMatch('/courses/:courseId/materials/:id');
  const inPlayer = Boolean(onLesson || onMaterial);
  return (
    <>
      {!inPlayer && <Nav />}
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/verify/:code" element={<VerifyCertificate />} />
        <Route path="/forbidden" element={<Forbidden />} />

        <Route element={<ProtectedRoute />}>
          <Route path="/feed" element={<Feed />} />
          <Route path="/leaderboard" element={<Leaderboard />} />
          <Route path="/hackathons" element={<Hackathons />} />
          <Route path="/interviews" element={<MockInterviews />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/profile/:id" element={<Profile />} />
          <Route path="/my-learning" element={<MyLearning />} />
          <Route path="/courses" element={<Courses />} />
          <Route path="/courses/:id" element={<CourseView />} />
          <Route path="/courses/:courseId/lessons/:lessonId" element={<LessonView />} />
          <Route path="/courses/:courseId/materials/:id" element={<MaterialView />} />
          <Route path="/courses/:courseId/assessments/:assessmentId" element={<AssessmentView />} />
          <Route path="/attempts/:attemptId" element={<AttemptView />} />
        </Route>

        <Route element={<AdminRoute />}>
          <Route path="/admin/dashboard" element={<Dashboard />} />
          <Route path="/admin/courses" element={<CourseList />} />
          <Route path="/admin/courses/new" element={<NewCourse />} />
          <Route path="/admin/courses/:id/edit" element={<CourseEditor />} />
          <Route path="/admin/courses/:id/questions" element={<QuestionBank />} />
          <Route path="/admin/courses/:id/results" element={<AssessmentResults />} />
          <Route path="/admin/users" element={<Users />} />
          <Route path="/admin/leaderboard" element={<AdminLeaderboard />} />
          <Route path="/admin/hackathons" element={<AdminHackathons />} />
          <Route path="/admin/interviews" element={<AdminMockInterviews />} />
          <Route path="/admin/audit" element={<AuditLog />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </>
  );
}
