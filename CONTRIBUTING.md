# Contributing to react-native-alarmkit

Thank you for your interest in contributing! 🎉

## Development Setup

1. Fork and clone the repository
```bash
git clone git@github.com:n8stowell82/react-native-alarmkit.git
cd react-native-alarmkit
```

2. Install dependencies
```bash
npm install
```

3. Build the project
```bash
npm run prepare
```

## Making Changes

### Code Style

- Follow the existing code style
- Use TypeScript for all new code
- Add JSDoc comments for public APIs
- Run type checking: `npm run typecheck`

### Testing

- Add tests for new features
- Ensure all tests pass: `npm test`
- Test on both iOS and Android if possible

### Commit Messages

Use clear, descriptive commit messages:
- `feat: Add support for custom alarm sounds`
- `fix: Resolve Android 12 permission issue`
- `docs: Update README with new examples`

## Pull Requests

1. Create a new branch for your feature:
```bash
git checkout -b feat/your-feature-name
```

2. Make your changes and commit:
```bash
git add .
git commit -m "feat: Add your feature"
```

3. Push to your fork:
```bash
git push origin feat/your-feature-name
```

4. Open a Pull Request on GitHub

### PR Guidelines

- Describe what your PR does
- Link any related issues
- Include screenshots/videos if UI changes
- Ensure CI passes
- Request review

## Reporting Issues

When reporting bugs, please include:

- Platform (iOS/Android)
- OS version
- React Native version
- Steps to reproduce
- Expected vs actual behavior
- Code sample if possible

## Feature Requests

We welcome feature requests! Please:

- Check if it's already requested
- Explain the use case
- Describe how it would work
- Consider contributing it yourself

## Code Review Process

- Maintainers will review PRs within a few days
- Address feedback promptly
- Be patient and respectful

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

Thank you for contributing! 🚀
