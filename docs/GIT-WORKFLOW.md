# Git Workflow

## Main branches

main

Stable/release branch.

develop

Integration branch.

## Feature branches

Format:

feature/issue-XX-description

Examples:

feature/issue-20-backend-setup

feature/issue-21-authentication

feature/issue-22-patients-api

## Bug branches

Format:

fix/issue-XX-description

## Pull requests

Normal workflow:

feature/*
↓
develop

Do not normally open feature PRs directly to main.

## Ticket rule

Every implementation should be associated with a GitHub Issue.

Do not begin large changes without a defined ticket.

## Commit scope

Keep commits focused.

Avoid unrelated refactors while completing a ticket.

## Database changes

Liquibase migrations must be committed together with code that requires the schema change.

Do not modify an already-applied migration.

Create a new migration instead.