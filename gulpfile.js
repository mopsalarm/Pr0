var gulp = require('gulp');
var scss = require('gulp-sass');
var autoprefixer = require('gulp-autoprefixer');
var jade = require('gulp-jade');
var plumber = require('gulp-plumber');
var ghPages = require('gulp-gh-pages')

gulp.task('default', ['scss', 'jade']);

gulp.task('publish', ['scss', 'jade'], function() {
  gulp.src(['css/*.css', 'img/**/*', 'index.html', 'unlocker.html', 'pr0gramm-navigator-unlock.apk'],
    {base: '.'})
    .pipe(gulp.dest('dist/'));
});

gulp.task('watch', function() {
  gulp.watch('scss/**/*.scss', ['scss']);
  gulp.watch('jade/**/*.jade', ['jade']);
});

gulp.task('scss', function() {
  gulp.src('scss/style.scss')
    .pipe(plumber())
    .pipe(scss().on('error', scss.logError))
    .pipe(autoprefixer({
      browsers: ['last 2 versions']
    }))
    .pipe(gulp.dest('css/'));
});

gulp.task('jade', function() {
  return gulp.src(['jade/index.jade', 'jade/unlocker.jade'])
    .pipe(plumber())
    .pipe(jade({pretty: true}))
    .pipe(gulp.dest('.'));
});

gulp.task('deploy', function() {
  return gulp.src('./dist/**/*').pipe(ghPages());
});
