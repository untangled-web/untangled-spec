tests:
	npm install
	lein with-profile test doo chrome automated-tests once
	lein test-refresh :run-once

help:
	@ make -rpn | sed -n -e '/^$$/ { n ; /^[^ ]*:/p; }' | sort | egrep --color '^[^ ]*:'

.PHONY: tests help
