/**
 * The interviewers a learner can choose. The ids match the server's list (it words the questions in each one's style); everything
 * here is about how they look, sound and talk between questions.
 *
 * `look` drives the 3D build: skin, hair and eye colours, a hair style, clothing, and small face-shape differences so no two people
 * share one face. `voice` lists browser voices to prefer in order; whatever the device has that matches first is used.
 */
export const PERSONAS = [
  {
    id: 'PRIYA',
    name: 'Priya',
    role: 'HR lead',
    style: 'Warm and encouraging',
    look: {
      skin: '#b57f5a', lip: '#a4574f', hair: '#1d1512', eyes: '#3b2417', hairStyle: 'bun', female: true,
      outfit: 'blazer', top: '#6b2c43', shirt: '#efe6dc', earrings: '#d8b25a', smile: 0.34,
      face: { width: 0.97, jaw: 0.92, chin: 0.98, nose: 1.02, lips: 1.12, eyes: 1.04, brow: 1.05, ridge: 0.7 },
    },
    voice: {
      female: true, rate: 0.98, pitch: 1.04, langs: ['en-IN', 'en-GB', 'en-US'],
      names: ['Neerja', 'Veena', 'Heera', 'Kalpana', 'Swara', 'Isha', 'Lekha', 'Moira', 'Tessa', 'Google UK English Female'],
    },
    lines: {
      hello: ["Hi, I'm Priya. Thanks for making the time today.", "Hello! I'm Priya, and I'll be chatting with you today."],
      next: ['Alright, next one.', "Okay, let's move on.", 'Great, here is the next one.', "Lovely. Let's keep going."],
      followUp: ["I'd love to hear a little more about that.", 'Let me pick up on something you said.'],
      strong: ['That was a really good answer.', 'I liked that, very clear.', 'Nicely put.'],
      fine: ['Thanks, that helps.', 'Okay, thank you.', 'Got it, thanks for sharing that.'],
      weak: ['Thanks for giving that a go.', "Okay, that's a start.", "Alright, no problem, let's keep going."],
      bye: ["That's all from me. Thank you, you did well to get through it. Let's see how it went."],
    },
  },
  {
    id: 'ARJUN',
    name: 'Arjun',
    role: 'Senior engineer',
    style: 'Direct and curious',
    look: {
      skin: '#a2704c', lip: '#8f5747', hair: '#15100e', eyes: '#2e1b12', hairStyle: 'short', female: false, beard: true, glasses: true,
      outfit: 'blazer', top: '#33394a', shirt: '#c9d9ea', smile: 0.16,
      face: { width: 1.03, jaw: 1.08, chin: 1.04, nose: 1.08, lips: 0.94, eyes: 0.98, brow: 1.3, ridge: 1.25 },
    },
    voice: {
      female: false, rate: 1.02, pitch: 0.96, langs: ['en-IN', 'en-GB', 'en-US'],
      names: ['Prabhat', 'Rishi', 'Ravi', 'Google UK English Male', 'Daniel'],
    },
    lines: {
      hello: ["Hey, I'm Arjun. Let's get into it.", "Hi, I'm Arjun, one of the engineers here. Shall we start?"],
      next: ['Okay. Next.', "Right, let's switch topics.", 'Moving on.', 'Next one.'],
      followUp: ['Let me push on that a bit.', 'Quick follow-up on that.'],
      strong: ['Good. That was solid.', 'Nice, that covers it.', 'Yep, that works.'],
      fine: ['Okay, fair enough.', 'Alright.', 'Okay, I follow.'],
      weak: ['Hmm, okay.', 'Alright, we can come back to that.', 'Okay, noted.'],
      bye: ["That's everything I had. Thanks, let's look at the results."],
    },
  },
  {
    id: 'SARAH',
    name: 'Sarah',
    role: 'Engineering manager',
    style: 'Calm and structured',
    look: {
      skin: '#e6c1a3', lip: '#b8706a', hair: '#6a3a22', eyes: '#566a3c', hairStyle: 'bob', female: true,
      outfit: 'blazer', top: '#565b64', shirt: '#f4f2ee', earrings: '#c9ccd2', smile: 0.24,
      face: { width: 0.96, jaw: 0.94, chin: 1.02, nose: 0.96, lips: 1.0, eyes: 1.02, brow: 0.9, ridge: 0.75 },
    },
    voice: {
      female: true, rate: 0.97, pitch: 1.02, langs: ['en-US', 'en-GB'],
      names: ['Jenny', 'Aria', 'Ava', 'Allison', 'Samantha', 'Zoe', 'Google US English', 'Serena', 'Sonia'],
    },
    lines: {
      hello: ["Hi, I'm Sarah. I manage one of the engineering teams. Let's take this one step at a time."],
      next: ["Okay. Let's look at the next one.", 'Thank you. Next question.', "Alright, let's move on."],
      followUp: ["I'd like to understand that a little better.", 'Can we go one level deeper there?'],
      strong: ['That was well structured.', 'Good, that was clear and specific.'],
      fine: ['Okay, thank you.', 'Thanks, that makes sense.'],
      weak: ['Okay. Take your time on the next one.', "Thank you. Let's keep going."],
      bye: ["That's the end of my questions. Thank you. Let's go through your results together."],
    },
  },
  {
    id: 'DAVID',
    name: 'David',
    role: 'Tech lead',
    style: 'Relaxed and upbeat',
    look: {
      skin: '#6a4430', lip: '#5e3a2e', hair: '#110c0a', eyes: '#24160f', hairStyle: 'fade', female: false,
      outfit: 'sweater', top: '#2f5a47', shirt: '#2f5a47', smile: 0.32,
      face: { width: 1.02, jaw: 1.05, chin: 1.0, nose: 1.14, lips: 1.1, eyes: 1.0, brow: 1.15, ridge: 1.1 },
    },
    voice: {
      female: false, rate: 1.0, pitch: 0.95, langs: ['en-GB', 'en-US', 'en-AU'],
      names: ['Ryan', 'Guy', 'Daniel', 'Arthur', 'Oliver', 'Google UK English Male', 'Alex'],
    },
    lines: {
      hello: ["Hey! I'm David, I lead one of the teams here. No pressure, let's just chat.", "Hi, I'm David. Good to meet you. Ready?"],
      next: ["Cool, let's do the next one.", 'Alright, next up.', 'Okay, here we go.', "Nice. Let's keep rolling."],
      followUp: ['Ooh, let me dig into that.', "Say a bit more about that, I'm curious."],
      strong: ['Love that answer.', 'Nice one!', "That's exactly the kind of thing I was after."],
      fine: ['Cool, thanks.', 'Okay, nice.', 'Makes sense.'],
      weak: ["No worries, that's a tricky one.", "All good, let's keep going."],
      bye: ["And that's a wrap! Thanks for hanging in there. Let's see how you did."],
    },
  },
  {
    id: 'MEI',
    name: 'Mei',
    role: 'Talent partner',
    style: 'Crisp and professional',
    look: {
      skin: '#e8c6a4', lip: '#b56866', hair: '#0f0c0b', eyes: '#2a1b13', hairStyle: 'long', female: true,
      outfit: 'blazer', top: '#1e1f24', shirt: '#f6f5f2', earrings: '#e0e0e0', smile: 0.2,
      face: { width: 0.98, jaw: 0.93, chin: 0.96, nose: 0.95, lips: 1.02, eyes: 0.98, brow: 0.95, ridge: 0.65 },
    },
    voice: {
      female: true, rate: 1.0, pitch: 1.03, langs: ['en-AU', 'en-GB', 'en-US', 'en-SG'],
      names: ['Natasha', 'Karen', 'Catherine', 'Libby', 'Sonia', 'Kate', 'Google UK English Female', 'Moira'],
    },
    lines: {
      hello: ["Hello, I'm Mei, from the talent team. Let's begin.", "Hi, I'm Mei. Thanks for joining. Shall we get started?"],
      next: ['Thank you. Next question.', 'Okay, moving on.', "Let's continue."],
      followUp: ['A quick follow-up on that.', "I'd like to clarify one point."],
      strong: ['Very good answer.', 'That was clear, thank you.'],
      fine: ['Thank you.', 'Noted, thank you.'],
      weak: ["Thank you. Let's move on.", 'Okay, noted.'],
      bye: ["That concludes the interview. Thank you for your time. Here's how you did."],
    },
  },
];

export const personaById = (id) => PERSONAS.find((p) => p.id === id) || PERSONAS[0];

/** The interviewer the server uses when none was picked: the HR lead for an HR round, an engineer otherwise. */
export const defaultPersonaFor = (interviewType) => (interviewType === 'HR' ? 'PRIYA' : 'ARJUN');

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}
