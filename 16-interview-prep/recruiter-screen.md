# Recruiter Screen

A 15–30 minute call, usually non-technical. The recruiter decides whether to put you in
front of engineers. They check: **fit for the role level, communication, logistics,
motivation, and red flags**. Being clear, honest and prepared is most of the game.

---

## 1. What they're checking

| They want to know | Your job |
|---|---|
| Can you explain your background clearly? | 60–75 s pitch ([`behavioral.md`](./behavioral.md) §3) |
| Does your experience match this level? | Explain why this role (and level) makes sense — honestly |
| Are there any red flags (gap, job changes)? | One calm, true sentence + pivot to evidence |
| Logistics: location, work authorization, start date, remote/hybrid | Know your answers precisely |
| Compensation alignment | Researched range for the *role level* |
| Motivation for *this* company | Two specific reasons |
| Technical baseline (sometimes) | Quick answers on your stack; see §4 |

---

## 2. Before the call

- [ ] Re-read the job posting; highlight 3 requirements and match each to a project or past role.
- [ ] Two specific reasons for this company (product, engineering blog, team, tech stack).
- [ ] Your pitch, timed. Your gap sentence, rehearsed ([`behavioral.md`](./behavioral.md) §4).
- [ ] Logistics answers written down: work authorization, location/relocation, earliest start date, notice period (if any), interview availability.
- [ ] Compensation range researched for the **junior/entry level** in that location (public salary data sites, levels data, posted ranges where legally required).
- [ ] Résumé in front of you, same version you submitted. Every line defensible ([`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md)).
- [ ] 3 questions for the recruiter (§6).
- [ ] Quiet room, charged phone, notes open, calendar open for scheduling next steps.

---

## 3. Common questions and answer shapes

| Question | Answer shape |
|---|---|
| "Tell me about yourself." | Present → past → why this role, 60–75 s. |
| "Why are you interested in this role/company?" | Two concrete reasons + one link to your experience/projects. |
| "I see a gap on your résumé — can you tell me about it?" | "Yes — I stepped away from software development in [year] for [true reason at your chosen level of detail]. I've spent the last six months rebuilding deliberately: [one project + one detail]. It's all on my GitHub." Stop. |
| "Your background is more senior than this role — why junior?" | Honest template from [`behavioral.md`](./behavioral.md) §5: be assessed on current skills, strong review culture, over-deliver at the right level. |
| "What tech stack are you strongest in?" (Track B answer — lead with the backend, not with your interview language) | "Java and Spring Boot with PostgreSQL and Redis on the back end — most recently ForgeCI, a CI/CD execution platform, and LedgerX, a double-entry ledger engine. I've also built React + TypeScript dashboards for FlowGrid and FlagForge." |
| "Are you interviewing elsewhere?" | "Yes, I'm in early stages with a few companies." (True, brief; no names needed.) If you have a deadline/offer, say so — it can speed things up. |
| "What are your salary expectations?" | See §5. |
| "When can you start?" | Truthful date. |
| "Are you authorized to work in [country]? Will you need sponsorship?" | Precise, truthful answer. |
| "Is this role's location/hybrid policy okay?" | Yes/no honestly. Don't agree to something you won't do. |
| "What kind of team are you looking for?" | Code review, mentorship, a product with users, ownership of a component. |

---

## 4. Light technical screening

Some recruiters read questions from a sheet and check keywords. Answer in 1–2 sentences, correctly, without jargon soup:

| Likely question | Crisp answer |
|---|---|
| "Difference between an interface and an abstract class in Java?" | Interface = a contract a class can implement many of (with default methods); abstract class = shared state/partial implementation, single inheritance. |
| "What's REST?" | An architectural style for HTTP APIs: resources identified by URLs, standard methods (GET/POST/PUT/DELETE), stateless requests, representations like JSON. |
| "SQL vs NoSQL?" | Relational: schema, joins, ACID transactions; NoSQL: flexible models, often easier horizontal scaling, weaker or different consistency. I've used PostgreSQL mostly and Redis for caching. |
| "What's Docker?" | Packages an app with its dependencies into an image that runs the same everywhere as a container. |
| "Have you used cloud services?" | "AWS: I deployed all four of my projects on EC2 with RDS PostgreSQL, S3 and CloudWatch, with least-privilege IAM roles — ForgeCI runs multiple worker instances." |
| "How do you test your code?" | JUnit 5 and Mockito for unit tests, Spring slice tests, Testcontainers for integration; CI runs them on every push. |

Deeper prep: [`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md).

---

## 5. Compensation

- Research the range for **this level** in **this location**. Your old title's salary is not the reference point; the role's band is.
- If asked first, you may deflect once: "I'd like to learn more about the role and level first — can you share the range for this position?" Many recruiters will.
- If pressed, give a researched **range** whose lower end you'd genuinely accept: "Based on what I've seen for junior roles in [city], somewhere around [X–Y], but I'm flexible depending on the total package."
- Never lie about current or past compensation. (In some jurisdictions employers can't ask; you can politely decline: "I'd prefer to focus on the range for this role.")

---

## 6. Questions to ask the recruiter

- "What does the interview process look like from here, and how long does it usually take?"
- "Is there an online assessment? Which platform and roughly what format?" → then [`../OA_PREP.md`](../OA_PREP.md)
- "Which language can I use in the coding rounds?" (You want Python for coding rounds; say so. If the coding round is "in the language of the job" and that's Java, you know now and prepare accordingly.)
- "What team would this role be on, and what does it work on?"
- "What do successful people in this role do well in their first six months?"
- "Is there anything in my background you'd like me to clarify for the hiring team?" (surfaces concerns while you can still address them)

---

## 7. After the call

- [ ] Within 24 h: short thank-you email; restate interest and one specific thing from the call.
- [ ] Log it in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md#recruiter-screens): date, company, role, stage, next step, concerns raised.
- [ ] Put the next step and any OA deadline in your calendar.
- [ ] Note any question you answered poorly; rewrite the answer today.

---

## 8. Red flags to avoid

- Reading a script word-for-word.
- Bad-mouthing a previous employer.
- Vague or evasive answers about the gap — the most common reason these calls go cold.
- Claiming skills you can't defend in the technical round.
- Not knowing what the company does.
- "I'll take any role" — sounds desperate and unfocused; name the role and why.
