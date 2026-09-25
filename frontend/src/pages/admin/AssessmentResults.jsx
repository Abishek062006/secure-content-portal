import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';

/** How learners are doing on a course's quizzes and assessments, and which questions trip them up. */
export default function AssessmentResults() {
  const { id } = useParams();
  const [course, setCourse] = useState(null);
  const [results, setResults] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([api.get(`/api/admin/courses/${id}`), api.get(`/api/admin/courses/${id}/assessment-results`)])
      .then(([outline, res]) => {
        setCourse(outline.course);
        setResults(res);
      })
      .catch((err) => setError(err.message));
  }, [id]);

  if (!results) {
    return <div className="container"><Alert error={error} /></div>;
  }

  return (
    <div className="container-wide">
      <Alert error={error} />
      <div className="page-head">
        <div>
          <p className="breadcrumb"><Link to={`/admin/courses/${id}/edit`}>← {course.title}</Link></p>
          <h1>Results</h1>
        </div>
      </div>

      {results.assessments.length === 0 ? (
        <div className="empty-state"><p>This course has no quizzes or assessments yet.</p></div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr><th>Title</th><th>Where</th><th>Type</th><th>Attempts</th><th>Learners</th><th>Average</th><th>Pass rate</th></tr>
            </thead>
            <tbody>
              {results.assessments.map((a) => (
                <tr key={a.id}>
                  <td>{a.title}</td>
                  <td>{a.scope}</td>
                  <td>{a.type === 'QUIZ' ? 'Quiz' : 'Assessment'}</td>
                  <td>{a.attempts}</td>
                  <td>{a.learners}</td>
                  <td>{a.averageScore != null ? `${a.averageScore}%` : '—'}</td>
                  <td>{a.passRatePercent != null ? `${a.passRatePercent}%` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2 className="editor-heading">Questions learners miss most</h2>
      {results.hardestQuestions.length === 0 ? (
        <div className="empty-state"><p>Nothing yet — this fills in once learners submit attempts.</p></div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead><tr><th>Question</th><th>Lesson</th><th>Difficulty</th><th>Answered</th><th>Correct</th></tr></thead>
            <tbody>
              {results.hardestQuestions.map((q) => (
                <tr key={q.id}>
                  <td>{q.text}</td>
                  <td>{q.lessonTitle}</td>
                  <td><span className={`badge difficulty-${q.difficulty.toLowerCase()}`}>{q.difficulty}</span></td>
                  <td>{q.answered}</td>
                  <td>{q.correctPercent}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
