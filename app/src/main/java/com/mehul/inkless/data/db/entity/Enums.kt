package com.mehul.inkless.data.db.entity

enum class Direction { DEBIT, CREDIT }

enum class TransactionType { EXPENSE, INCOME, SELF_TRANSFER, REFUND, SPLIT_SETTLEMENT, CC_BILL }

enum class TransactionStatus { AUTO, CONFIRMED, NEEDS_REVIEW }

enum class CaptureSource { SMS, NOTIFICATION, MANUAL, SCAN_PAY }

enum class RawCaptureStatus { PARSED, UNPARSED, IGNORED }

/** DIRTY rows have local changes the cloud hasn't seen yet. */
enum class SyncState { SYNCED, DIRTY }
