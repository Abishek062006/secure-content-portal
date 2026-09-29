import { Route, Routes, useMatch } from 'react-router-dom';
import Nav from './components/Nav';
import { ProtectedRoute, AdminRoute } from './components/ProtectedRoute';
import Home from './pages/Home';
import Login from './pages/Login';
import Courses from './pages/Courses';
import CourseView from './pages/CourseView';
import LessonView from './pages/LessonView';
import Leaderboard from './pages/Leaderboard';
import Hackathons from './pages/Hackathons';
import HackathonDetail from './pages/HackathonDetail';
import JoinTeam from './pages/JoinTeam';
import Judging from './pages/Judging';
import AdminHackathonManage from './pages/admin/AdminHackathonManage';
import InterviewPrep from './pages/InterviewPrep';
import InterviewSession from './pages/InterviewSession';
import AdminHackathons from './pages/admin/AdminHackathons';
import MaterialView from './pages/MaterialView';
import AssessmentView from './pages/AssessmentView';
import AttemptView from './pages/AttemptView';
import MyLearning from './pages/MyLearning';
import Notifications from './pages/Notifications';
import PaymentSuccess from './pages/PaymentSuccess';
import Progress from './pages/Progress';
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
import AdminRegistrations from './pages/admin/AdminRegistrations';
import AdminEnquiries from './pages/admin/AdminEnquiries';


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
          <Route path="/profile" element={<Profile />} />
          <Route path="/profile/:id" element={<Profile />} />
          <Route path="/my-learning" element={<MyLearning />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/payment/success" element={<PaymentSuccess />} />
          <Route path="/progress" element={<Progress />} />
          <Route path="/leaderboard" element={<Leaderboard />} />
          <Route path="/hackathons" element={<Hackathons />} />
          <Route path="/hackathons/join/:code" element={<JoinTeam />} />
          <Route path="/hackathons/:id" element={<HackathonDetail />} />
          <Route path="/judging" element={<Judging />} />
          <Route path="/interview" element={<InterviewPrep />} />
          <Route path="/interview/:id" element={<InterviewSession />} />
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
          <Route path="/admin/hackathons" element={<AdminHackathons />} />
          <Route path="/admin/hackathons/:id/manage" element={<AdminHackathonManage />} />
          <Route path="/admin/users" element={<Users />} />
          <Route path="/admin/audit" element={<AuditLog />} />
          <Route path="/admin/registrations" element={<AdminRegistrations />} />
          <Route path="/admin/enquiries" element={<AdminEnquiries />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </>
  );
}
