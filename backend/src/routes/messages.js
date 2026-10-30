// routes/messages.js - direct messages between a volunteer and the organization behind a listing.
//
// One conversation exists per (listing, volunteer) pair, and only those two people can read or write
// it. Its id is built from the pair, so asking to open it twice (a retry, a double tap) can never
// create a second thread. A volunteer can only start one for a listing they signed up to.

import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { requireRole } from '../middleware/auth.js';
import { forbidden, notFound } from '../errors.js';
import { ACTIVE_SIGNUP_STATUSES } from '../constants.js';
import { bodyOf, parseMessage, parseSince, requireString } from '../validators.js';
import { conversationResponse, messageResponse } from '../serialize.js';
import { loadUserNames } from '../lookups.js';

const PREVIEW_LENGTH = 120;
const MAX_MESSAGES = 200;
const millis = (ts) => ts?.toMillis?.() ?? 0;

export const conversationIdFor = (listingId, volunteerId) => `${listingId}_${volunteerId}`;

/** Messages. Mounted at the API root because the paths span /listings and /conversations. */
export function messagesRouter({ db }) {
  const r = Router();
  const anyRole = requireRole('volunteer', 'organization');

  // Loads a conversation and makes sure the caller is one of its two people.
  async function loadForParticipant(id, userId, t = null) {
    const ref = db.collection('conversations').doc(id);
    const snap = await (t ? t.get(ref) : ref.get());
    if (!snap.exists) throw notFound('Conversation not found.');
    const conv = snap.data();
    if (conv.volunteerId !== userId && conv.organizationId !== userId) {
      throw forbidden('That conversation is not yours.');
    }
    return { ref, conv };
  }

  // Open (or fetch) the conversation about a listing.
  //   volunteer: opens a thread with the listing's organization - needs an active sign-up.
  //   organization: opens one with a volunteer who signed up to ITS listing - body { volunteerId }.
  // POST /listings/{id}/conversations  ->  201 if new, 200 if it already existed
  r.post(
    '/listings/:id/conversations',
    anyRole,
    ah(async (req, res) => {
      const listingSnap = await db.collection('listings').doc(req.params.id).get();
      if (!listingSnap.exists) throw notFound('Listing not found.');
      const listing = listingSnap.data();

      let volunteerId;
      if (req.user.role === 'volunteer') {
        volunteerId = req.user.id; // always the caller, never taken from the body
      } else {
        if (listing.organizationId !== req.user.id) {
          throw forbidden('You can only message volunteers on your own listings.');
        }
        volunteerId = requireString(bodyOf(req).volunteerId, 'volunteerId');
      }

      const signups = await db.collection('signups').where('listingId', '==', listingSnap.id).get();
      const signedUp = signups.docs.some((d) => {
        const s = d.data();
        return s.volunteerId === volunteerId && ACTIVE_SIGNUP_STATUSES.includes(s.status);
      });
      if (!signedUp) {
        throw req.user.role === 'volunteer'
          ? forbidden('You can only message an organization about a listing you signed up for.', 'not_signed_up')
          : notFound('That volunteer is not signed up to this listing.');
      }

      const id = conversationIdFor(listingSnap.id, volunteerId);
      const ref = db.collection('conversations').doc(id);
      const { conv, created } = await db.runTransaction(async (t) => {
        const existing = await t.get(ref);
        if (existing.exists) return { conv: existing.data(), created: false };
        const data = {
          id,
          listingId: listingSnap.id,
          listingTitle: listing.title, // denormalized, so the thread survives the listing being deleted
          organizationId: listing.organizationId,
          volunteerId,
          lastMessage: null,
          lastMessageAt: null,
          lastSenderId: null,
          unread: {},
          createdAt: Timestamp.now(),
        };
        t.create(ref, data);
        return { conv: data, created: true };
      });

      const names = await loadUserNames(db, [conv.organizationId, conv.volunteerId]);
      res.status(created ? 201 : 200).json(conversationResponse(id, conv, req.user.id, names));
    })
  );

  // My conversations, most recently active first, with my unread count on each.
  // GET /conversations
  r.get(
    '/conversations',
    anyRole,
    ah(async (req, res) => {
      const field = req.user.role === 'organization' ? 'organizationId' : 'volunteerId';
      const snap = await db.collection('conversations').where(field, '==', req.user.id).get();
      const convs = snap.docs
        .map((d) => ({ ...d.data(), id: d.id }))
        // A thread nobody has written in yet falls back to when it was opened.
        .sort((a, b) => (millis(b.lastMessageAt) || millis(b.createdAt)) - (millis(a.lastMessageAt) || millis(a.createdAt)));
      const names = await loadUserNames(db, convs.flatMap((c) => [c.organizationId, c.volunteerId]));
      res.json(convs.map((c) => conversationResponse(c.id, c, req.user.id, names)));
    })
  );

  // The messages in one conversation, oldest first. ?since=<ISO date> returns only newer ones.
  // GET /conversations/{id}/messages
  r.get(
    '/conversations/:id/messages',
    anyRole,
    ah(async (req, res) => {
      await loadForParticipant(req.params.id, req.user.id);
      const since = parseSince(req.query.since);
      const snap = await db.collection('messages').where('conversationId', '==', req.params.id).get();
      let rows = snap.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => millis(a.sentAt) - millis(b.sentAt));
      if (since) rows = rows.filter((m) => millis(m.sentAt) > since.getTime());
      res.json(rows.slice(-MAX_MESSAGES).map((m) => messageResponse(m.id, m)));
    })
  );

  // Send a message. The message, the conversation's preview and the other person's unread count
  // change together, so it runs in a transaction.
  // POST /conversations/{id}/messages - body { text }  ->  201 with the message
  r.post(
    '/conversations/:id/messages',
    anyRole,
    ah(async (req, res) => {
      const { text } = parseMessage(bodyOf(req));
      const created = await db.runTransaction(async (t) => {
        const { ref, conv } = await loadForParticipant(req.params.id, req.user.id, t);
        const recipientId = conv.organizationId === req.user.id ? conv.volunteerId : conv.organizationId;
        const now = Timestamp.now();
        const msgRef = db.collection('messages').doc();
        const message = {
          id: msgRef.id,
          conversationId: ref.id,
          senderId: req.user.id, // always the caller
          text,
          sentAt: now,
        };
        t.create(msgRef, message);
        t.update(ref, {
          lastMessage: text.length > PREVIEW_LENGTH ? `${text.slice(0, PREVIEW_LENGTH - 1)}…` : text,
          lastMessageAt: now,
          lastSenderId: req.user.id,
          unread: { ...(conv.unread ?? {}), [recipientId]: (conv.unread?.[recipientId] ?? 0) + 1 },
        });
        return message;
      });
      res.status(201).json(messageResponse(created.id, created));
    })
  );

  // The caller has read everything in this conversation.
  // PATCH /conversations/{id}/read  ->  204
  r.patch(
    '/conversations/:id/read',
    anyRole,
    ah(async (req, res) => {
      await db.runTransaction(async (t) => {
        const { ref, conv } = await loadForParticipant(req.params.id, req.user.id, t);
        if ((conv.unread?.[req.user.id] ?? 0) === 0) return; // nothing to clear
        t.update(ref, { unread: { ...conv.unread, [req.user.id]: 0 } });
      });
      res.status(204).end();
    })
  );

  return r;
}
