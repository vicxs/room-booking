# Contract-first with OpenAPI and generated interfaces

The API is defined first in `openapi.yaml`, from which the Spring interfaces (`interfaceOnly`) and the TypeScript client are generated, so an implementation that drifts from the Contract does not compile. We prefer this over code-first (springdoc) because the Contract is agreed in the Spec before building and lands as the PR's first commit, easy to review; the remaining Work items build on it and cannot change it without re-aligning.
