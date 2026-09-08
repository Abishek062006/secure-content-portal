import { Route, Routes } from 'react-router-dom';
import Nav from './components/Nav';
import { ProtectedRoute, AdminRoute } from './components/ProtectedRoute';
import Home from './pages/Home';
import Login from './pages/Login';
import Library from './pages/Library';
import ContentView from './pages/ContentView';
import NotFound from './pages/NotFound';
import Forbidden from './pages/Forbidden';
import ContentList from './pages/admin/ContentList';
import UploadContent from './pages/admin/UploadContent';
import EditContent from './pages/admin/EditContent';
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
        </Route>

        <Route element={<AdminRoute />}>
          <Route path="/admin/content" element={<ContentList />} />
          <Route path="/admin/content/new" element={<UploadContent />} />
          <Route path="/admin/content/:id/edit" element={<EditContent />} />
          <Route path="/admin/users" element={<Users />} />
          <Route path="/admin/audit" element={<AuditLog />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </>
  );
}
