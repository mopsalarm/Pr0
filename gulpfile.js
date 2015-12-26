var gulp = require('gulp');
var scss = require('gulp-sass');
var autoprefixer = require('gulp-autoprefixer');
var jade = require('gulp-jade');
var plumber = require('gulp-plumber');
var ghPages = require('gulp-gh-pages');
var clean = require('gulp-clean');

gulp.task('default', ['scss', 'jade']);

gulp.task('deploy', ['build'], function() {
  return gulp.src('./dist/**/*').pipe(ghPages());
});

gulp.task('build', ['scss', 'jade'], function() {
  return gulp.src(['css/*.css', 'img/**/*'], {base: '.'})
    .pipe(gulp.dest('dist/'));
});

gulp.task('watch', function() {
  gulp.watch('scss/**/*.scss', ['scss']);
  gulp.watch('jade/**/*.jade', ['jade']);
  gulp.watch('img/**', ['build']);
});

gulp.task('clean', function() {
    return gulp.src("dist", {read: false}).pipe(clean());
});

gulp.task('scss', function() {
  return gulp.src('scss/style.scss')
    .pipe(plumber())
    .pipe(scss().on('error', scss.logError))
    .pipe(autoprefixer({
      browsers: ['last 2 versions']
    }))
    .pipe(gulp.dest('dist/css/'));
});

gulp.task('jade', function() {
  return gulp.src(['jade/index.jade'])
    .pipe(plumber())
    .pipe(jade({pretty: true}))
    .pipe(gulp.dest('dist/'));
});
